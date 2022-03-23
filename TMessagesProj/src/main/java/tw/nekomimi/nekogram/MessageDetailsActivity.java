package tw.nekomimi.nekogram;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.helpers.EntitiesHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

@SuppressLint({"RtlHardcoded", "NotifyDataSetChanged"})
public class MessageDetailsActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private final MessageObject messageObject;
    private final boolean noforwards;

    private TLRPC.Chat toChat;
    private TLRPC.User fromUser;
    private TLRPC.Chat fromChat;
    private TLRPC.Chat forwardFromChat;
    private TLRPC.User forwardFromUser;
    private String filePath;
    private String fileName;
    private Runnable unregisterFlagSecure;

    private int idRow;
    private int messageRow;
    private int captionRow;
    private int groupRow;
    private int channelRow;
    private int fromRow;
    private int botRow;
    private int dateRow;
    private int editedRow;
    private int forwardRow;
    private int fileNameRow;
    private int filePathRow;
    private int fileSizeRow;
    private int dcRow;
    private int restrictionReasonRow;
    private int forwardsRow;
    private int sponsoredRow;
    private int shouldBlockMessageRow;
    private int languageRow;
    private int linkOrEmojiOnlyRow;
    private int endRow;

    public MessageDetailsActivity(MessageObject messageObject) {
        this.messageObject = messageObject;

        noforwards = getMessagesController().isChatNoForwards(toChat) || messageObject.messageOwner.noforwards;

        if (messageObject.messageOwner.peer_id != null) {
            if (messageObject.messageOwner.peer_id.channel_id != 0) {
                toChat = getMessagesController().getChat(messageObject.messageOwner.peer_id.channel_id);
            } else if (messageObject.messageOwner.peer_id.chat_id != 0) {
                toChat = getMessagesController().getChat(messageObject.messageOwner.peer_id.chat_id);
            }
        }
        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
            if (messageObject.messageOwner.fwd_from.from_id.channel_id != 0) {
                forwardFromChat = getMessagesController().getChat(messageObject.messageOwner.fwd_from.from_id.channel_id);
            } else if (messageObject.messageOwner.fwd_from.from_id.chat_id != 0) {
                forwardFromChat = getMessagesController().getChat(messageObject.messageOwner.fwd_from.from_id.chat_id);
            } else if (messageObject.messageOwner.fwd_from.from_id.user_id != 0) {
                forwardFromUser = getMessagesController().getUser(messageObject.messageOwner.fwd_from.from_id.user_id);
            }
        }

        if (messageObject.messageOwner.from_id.user_id != 0) {
            fromUser = getMessagesController().getUser(messageObject.messageOwner.from_id.user_id);
        } else if (messageObject.messageOwner.from_id.channel_id != 0) {
            fromChat = getMessagesController().getChat(messageObject.messageOwner.from_id.channel_id);
        }

        filePath = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(filePath)) {
            File temp = new File(filePath);
            if (!temp.exists()) {
                filePath = null;
            }
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = FileLoader.getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(filePath);
            if (!temp.exists()) {
                filePath = null;
            }
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = FileLoader.getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(filePath);
            if (!temp.isFile()) {
                filePath = null;
            }
        }

        if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.document != null) {
            if (TextUtils.isEmpty(messageObject.messageOwner.media.document.file_name)) {
                for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                    if (messageObject.messageOwner.media.document.attributes.get(a) instanceof TLRPC.TL_documentAttributeFilename) {
                        fileName = messageObject.messageOwner.media.document.attributes.get(a).file_name;
                    }
                }
            } else {
                fileName = messageObject.messageOwner.media.document.file_name;
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        return true;
    }

    @Override
    protected boolean hasWhiteActionBar() {
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkNoForwards() {
        if (noforwards) {
            if (getMessagesController().isChatNoForwards(toChat)) {
                BulletinFactory.of(this).createErrorBulletin(toChat.broadcast ?
                        LocaleController.getString("ForwardsRestrictedInfoChannel", R.string.ForwardsRestrictedInfoChannel) :
                        LocaleController.getString("ForwardsRestrictedInfoGroup", R.string.ForwardsRestrictedInfoGroup)
                ).show();
            } else {
                BulletinFactory.of(this).createErrorBulletin(
                        LocaleController.getString("ForwardsRestrictedInfoBot", R.string.ForwardsRestrictedInfoBot)).show();
            }
        }
        return noforwards;
    }

    @SuppressLint({"NewApi", "RtlHardcoded"})
    @Override
    public View createView(Context context) {
        View fragmentView = super.createView(context);
        actionBar.setTitle(LocaleController.getString("MessageDetails", R.string.MessageDetails));

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position != endRow) {
                if (!checkNoForwards() || !(position == messageRow || position == captionRow)) {
                    TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;
                    AndroidUtilities.addToClipboard(EntitiesHelper.commonizeSpans(textCell.getValueTextView().getText()));
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString("TextCopied", R.string.TextCopied)).show();
                }
            }

        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position == filePathRow) {
                if (!checkNoForwards()) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    var uri = FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", new File(filePath));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setDataAndType(uri, messageObject.getMimeType());
                    startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                }
            } else if (position == channelRow || position == groupRow) {
                if (toChat != null) {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", toChat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                }
            } else if (position == fromRow) {
                if (fromUser != null) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", fromUser.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                } else if (fromChat != null) {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", fromChat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                }
            } else if (position == forwardRow) {
                if (forwardFromUser != null) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", forwardFromUser.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                } else if (forwardFromChat != null) {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", forwardFromChat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    presentFragment(fragment);
                }
            } else if (position == restrictionReasonRow) {
                ArrayList<TLRPC.TL_restrictionReason> reasons = messageObject.messageOwner.restriction_reason;
                LinearLayout ll = new LinearLayout(context);
                ll.setOrientation(LinearLayout.VERTICAL);

                AlertDialog dialog = new AlertDialog.Builder(context)
                        .setView(ll)
                        .create();

                for (TLRPC.TL_restrictionReason reason : reasons) {
                    TextDetailSettingsCell cell = new TextDetailSettingsCell(context);
                    cell.setBackground(Theme.getSelectorDrawable(false));
                    cell.setMultilineDetail(true);
                    cell.setOnClickListener(v1 -> {
                        dialog.dismiss();
                        AndroidUtilities.addToClipboard(cell.getValueTextView().getText());
                        BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString("TextCopied", R.string.TextCopied)).show();
                    });
                    cell.setTextAndValue(reason.reason + "-" + reason.platform, reason.text, false);

                    ll.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }

                showDialog(dialog);
            } else {
                return false;
            }
            return true;
        });

        if (noforwards) {
            unregisterFlagSecure = AndroidUtilities.registerFlagSecure(getParentActivity().getWindow());
        }

        return fragmentView;
    }

    @Override
    protected void updateRows() {
        rowCount = 0;
        idRow = messageObject.isSponsored() ? -1 : rowCount++;
        messageRow = TextUtils.isEmpty(messageObject.messageText) ? -1 : rowCount++;
        captionRow = TextUtils.isEmpty(messageObject.caption) ? -1 : rowCount++;
        groupRow = toChat != null && !toChat.broadcast ? rowCount++ : -1;
        channelRow = toChat != null && toChat.broadcast ? rowCount++ : -1;
        fromRow = fromUser != null || fromChat != null || messageObject.messageOwner.post_author != null ? rowCount++ : -1;
        botRow = fromUser != null && fromUser.bot ? rowCount++ : -1;
        dateRow = messageObject.messageOwner.date != 0 ? rowCount++ : -1;
        editedRow = messageObject.messageOwner.edit_date != 0 ? rowCount++ : -1;
        forwardRow = messageObject.isForwarded() ? rowCount++ : -1;
        fileNameRow = TextUtils.isEmpty(fileName) ? -1 : rowCount++;
        filePathRow = TextUtils.isEmpty(filePath) ? -1 : rowCount++;
        fileSizeRow = messageObject.getSize() != 0 ? rowCount++ : -1;
        if (messageObject.messageOwner.media != null && (
                (messageObject.messageOwner.media.photo != null && messageObject.messageOwner.media.photo.dc_id > 0) ||
                        (messageObject.messageOwner.media.document != null && messageObject.messageOwner.media.document.dc_id > 0)
        )) {
            dcRow = rowCount++;
        } else {
            dcRow = -1;
        }
        restrictionReasonRow = messageObject.messageOwner.restriction_reason.isEmpty() ? -1 : rowCount++;
        forwardsRow = messageObject.messageOwner.forwards > 0 ? rowCount++ : -1;
        sponsoredRow = messageObject.isSponsored() ? rowCount++ : -1;
        shouldBlockMessageRow = messageObject.shouldBlockMessage() ? rowCount++ : -1;
        languageRow = TextUtils.isEmpty(getMessageHelper().getMessagePlainText(messageObject)) ? -1 : rowCount++;
        linkOrEmojiOnlyRow = !TextUtils.isEmpty(messageObject.messageOwner.message) && getMessageHelper().isLinkOrEmojiOnlyMessage(messageObject) ? rowCount++ : -1;
        endRow = rowCount++;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (unregisterFlagSecure != null) {
            unregisterFlagSecure.run();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 6: {
                    TextDetailSettingsCell textCell = (TextDetailSettingsCell) holder.itemView;
                    textCell.setMultilineDetail(true);
                    boolean divider = position + 1 != endRow;
                    if (position == idRow) {
                        textCell.setTextAndValue("ID", String.valueOf(messageObject.messageOwner.id), divider);
                    } else if (position == messageRow) {
                        textCell.setTextAndValue("Message", messageObject.messageText, divider);
                    } else if (position == captionRow) {
                        textCell.setTextAndValue("Caption", messageObject.caption, divider);
                    } else if (position == channelRow || position == groupRow) {
                        StringBuilder builder = new StringBuilder();
                        builder.append(toChat.title);
                        builder.append("\n");
                        if (!TextUtils.isEmpty(toChat.username)) {
                            builder.append("@");
                            builder.append(toChat.username);
                            builder.append("\n");
                        }
                        builder.append(toChat.id);
                        textCell.setTextAndValue(position == channelRow ? "Channel" : "Group", builder.toString(), divider);
                    } else if (position == fromRow) {
                        StringBuilder builder = new StringBuilder();
                        if (fromUser != null) {
                            builder.append(ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                            builder.append("\n");
                            if (!TextUtils.isEmpty(fromUser.username)) {
                                builder.append("@");
                                builder.append(fromUser.username);
                                builder.append("\n");
                            }
                            builder.append(fromUser.id);
                        } else if (fromChat != null) {
                            builder.append(fromChat.title);
                            builder.append("\n");
                            if (!TextUtils.isEmpty(fromChat.username)) {
                                builder.append("@");
                                builder.append(fromChat.username);
                                builder.append("\n");
                            }
                            builder.append(fromChat.id);
                        } else if (!TextUtils.isEmpty(messageObject.messageOwner.post_author)) {
                            builder.append(messageObject.messageOwner.post_author);
                        }
                        textCell.setTextAndValue("From", builder.toString(), divider);
                    } else if (position == botRow) {
                        textCell.setTextAndValue("Bot", "Yes", divider);
                    } else if (position == dateRow) {
                        long date = (long) messageObject.messageOwner.date * 1000;
                        textCell.setTextAndValue(messageObject.scheduled ? "Scheduled date" : "Date", messageObject.messageOwner.date == 0x7ffffffe ? "When online" : LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(date)), LocaleController.getInstance().formatterDayWithSeconds.format(new Date(date))), divider);
                    } else if (position == editedRow) {
                        long date = (long) messageObject.messageOwner.edit_date * 1000;
                        textCell.setTextAndValue("Edited", LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(date)), LocaleController.getInstance().formatterDayWithSeconds.format(new Date(date))), divider);
                    } else if (position == forwardRow) {
                        StringBuilder builder = new StringBuilder();
                        if (messageObject.messageOwner.fwd_from.from_id != null) {
                            if (messageObject.messageOwner.fwd_from.from_id.channel_id != 0) {
                                TLRPC.Chat chat = getMessagesController().getChat(messageObject.messageOwner.fwd_from.from_id.channel_id);
                                builder.append(chat.title);
                                builder.append("\n");
                                if (!TextUtils.isEmpty(chat.username)) {
                                    builder.append("@");
                                    builder.append(chat.username);
                                    builder.append("\n");
                                }
                                builder.append(chat.id);
                            } else if (messageObject.messageOwner.fwd_from.from_id.user_id != 0) {
                                TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.fwd_from.from_id.user_id);
                                builder.append(ContactsController.formatName(user.first_name, user.last_name));
                                builder.append("\n");
                                if (!TextUtils.isEmpty(user.username)) {
                                    builder.append("@");
                                    builder.append(user.username);
                                    builder.append("\n");
                                }
                                builder.append(user.id);
                            } else if (messageObject.messageOwner.fwd_from.from_id.chat_id != 0) {
                                TLRPC.Chat chat = getMessagesController().getChat(messageObject.messageOwner.fwd_from.from_id.chat_id);
                                builder.append(chat.title);
                                builder.append("\n");
                                if (!TextUtils.isEmpty(chat.username)) {
                                    builder.append("@");
                                    builder.append(chat.username);
                                    builder.append("\n");
                                }
                                builder.append(chat.id);
                            }
                        } else if (!TextUtils.isEmpty(messageObject.messageOwner.fwd_from.from_name)) {
                            builder.append(messageObject.messageOwner.fwd_from.from_name);
                        }
                        textCell.setTextAndValue("Forward from", builder.toString(), divider);
                    } else if (position == fileNameRow) {
                        textCell.setTextAndValue("File name", fileName, divider);
                    } else if (position == filePathRow) {
                        textCell.setTextAndValue("File path", filePath, divider);
                    } else if (position == fileSizeRow) {
                        textCell.setTextAndValue("File size", AndroidUtilities.formatFileSize(messageObject.getSize()), divider);
                    } else if (position == dcRow) {
                        int dc = 0;
                        if (messageObject.messageOwner.media.photo != null && messageObject.messageOwner.media.photo.dc_id > 0) {
                            dc = messageObject.messageOwner.media.photo.dc_id;
                        } else if (messageObject.messageOwner.media.document != null && messageObject.messageOwner.media.document.dc_id > 0) {
                            dc = messageObject.messageOwner.media.document.dc_id;
                        }
                        textCell.setTextAndValue("DC", String.format(Locale.US, "%d, %s", dc, getMessageHelper().getDCLocation(dc)), divider);
                    } else if (position == restrictionReasonRow) {
                        ArrayList<TLRPC.TL_restrictionReason> reasons = messageObject.messageOwner.restriction_reason;
                        StringBuilder value = new StringBuilder();
                        for (TLRPC.TL_restrictionReason reason : reasons) {
                            value.append(reason.reason);
                            value.append("-");
                            value.append(reason.platform);
                            if (reasons.indexOf(reason) != reasons.size() - 1) {
                                value.append(", ");
                            }
                        }
                        textCell.setTextAndValue("Restriction reason", value.toString(), divider);
                    } else if (position == forwardsRow) {
                        textCell.setTextAndValue("Forwards", String.format(Locale.US, "%d", messageObject.messageOwner.forwards), divider);
                    } else if (position == sponsoredRow) {
                        textCell.setTextAndValue("Sponsored", "Yes", divider);
                    } else if (position == shouldBlockMessageRow) {
                        textCell.setTextAndValue("Blocked", "Yes", divider);
                    } else if (position == languageRow) {
                        textCell.setTextAndValue("Language", "", divider);
                        LanguageDetector.detectLanguage(
                                getMessageHelper().getMessagePlainText(messageObject),
                                lang -> textCell.setTextAndValue("Language", lang, divider),
                                e -> textCell.setTextAndValue("Language", e.getLocalizedMessage(), divider));
                    } else if (position == linkOrEmojiOnlyRow) {
                        textCell.setTextAndValue("Link or emoji only", "Yes", divider);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == endRow) {
                return 1;
            } else {
                return 6;
            }
        }
    }
}
