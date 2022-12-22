package io.agora.chat.thread.presenter;

import android.net.Uri;
import android.text.TextUtils;

import com.hyphenate.EMCallBack;
import com.hyphenate.EMError;
import com.hyphenate.EMValueCallBack;
import com.hyphenate.chat.EMChatThread;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMGroup;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMMessageBody;
import com.hyphenate.easeui.constants.EaseConstant;
import com.hyphenate.easeui.manager.EaseAtMessageHelper;
import com.hyphenate.easeui.modules.menu.EaseChatType;
import com.hyphenate.easeui.utils.EaseCommonUtils;
import com.hyphenate.easeui.utils.EaseFileUtils;
import com.hyphenate.util.EMLog;


public class EaseChatThreadCreatePresenterImpl extends EaseChatThreadCreatePresenter {
    private static final String TAG = EaseChatThreadCreatePresenterImpl.class.getSimpleName();

    @Override
    public void sendTextMessage(String content) {
        if(TextUtils.isEmpty(content)) {
            EMLog.e(TAG, "sendTextMessage : message content is empty");
            return;
        }
        sendTextMessage(content, false);
    }

    @Override
    public void sendTextMessage(String content, boolean isNeedGroupAck) {
        if(EaseAtMessageHelper.get().containsAtUsername(content)) {
            sendAtMessage(content);
            return;
        }
        EMMessage message = EMMessage.createTxtSendMessage(content, toChatUsername);
        message.setIsNeedGroupAck(isNeedGroupAck);
        setMessage(message);
    }

    @Override
    public void sendAtMessage(String content) {
        if(!isGroupChat()){
            EMLog.e(TAG, "only support group chat message");
            if(isActive()) {
                runOnUI(()-> mView.sendMessageFail("only support group chat message"));
            }
            return;
            
        }
        EMMessage message = EMMessage.createTxtSendMessage(content, toChatUsername);
        EMGroup group = EMClient.getInstance().groupManager().getGroup(toChatUsername);
        if(EMClient.getInstance().getCurrentUser().equals(group.getOwner()) && EaseAtMessageHelper.get().containsAtAll(content)){
            message.setAttribute(EaseConstant.MESSAGE_ATTR_AT_MSG, EaseConstant.MESSAGE_ATTR_VALUE_AT_MSG_ALL);
        }else {
            message.setAttribute(EaseConstant.MESSAGE_ATTR_AT_MSG,
                    EaseAtMessageHelper.get().atListToJsonArray(EaseAtMessageHelper.get().getAtMessageUsernames(content)));
        }
        setMessage(message);
    }

    @Override
    public void sendBigExpressionMessage(String name, String identityCode) {
        EMMessage message = EaseCommonUtils.createExpressionMessage(toChatUsername, name, identityCode);
        setMessage(message);
    }

    @Override
    public void sendVoiceMessage(Uri filePath, int length) {
        EMMessage message = EMMessage.createVoiceSendMessage(filePath, length, toChatUsername);
        setMessage(message);
    }

    @Override
    public void sendImageMessage(Uri imageUri) {
        sendImageMessage(imageUri, false);
    }

    @Override
    public void sendGroupDingMessage(EMMessage message) {
        setMessage(message);
    }

    @Override
    public void sendImageMessage(Uri imageUri, boolean sendOriginalImage) {
        EMMessage message = EMMessage.createImageSendMessage(imageUri, sendOriginalImage, toChatUsername);
        setMessage(message);
    }

    @Override
    public void sendLocationMessage(double latitude, double longitude, String locationAddress) {
        EMMessage message = EMMessage.createLocationSendMessage(latitude, longitude, locationAddress, toChatUsername);
        EMLog.i(TAG, "current = "+EMClient.getInstance().getCurrentUser() + " to = "+toChatUsername);
        EMMessageBody body = message.getBody();
        String msgId = message.getMsgId();
        String from = message.getFrom();
        EMLog.i(TAG, "body = "+body);
        EMLog.i(TAG, "msgId = "+msgId + " from = "+from);
        setMessage(message);
    }

    @Override
    public void sendVideoMessage(Uri videoUri, int videoLength) {
        String thumbPath = EaseFileUtils.getThumbPath(mView.context(), videoUri);
        EMMessage message = EMMessage.createVideoSendMessage(videoUri, thumbPath, videoLength, toChatUsername);
        setMessage(message);
    }

    @Override
    public void sendFileMessage(Uri fileUri) {
        EMMessage message = EMMessage.createFileSendMessage(fileUri, toChatUsername);
        setMessage(message);
    }

    @Override
    public void addMessageAttributes(EMMessage message) {
        //You can add some custom attributes
        mView.addMsgAttrBeforeSend(message);
    }

    @Override
    public void createThread(String threadName, EMMessage message) {
        if(TextUtils.isEmpty(threadName)) {
            mView.onCreateThreadFail(EMError.GENERAL_ERROR, "Thread name should not be null");
            return;
        }
        EMClient.getInstance().chatThreadManager().createChatThread(parentId, messageId, threadName, new EMValueCallBack<EMChatThread>() {
            @Override
            public void onSuccess(EMChatThread value) {
                toChatUsername = value.getChatThreadId();
                if(isActive()) {
                    runOnUI(()->mView.onCreateThreadSuccess(value, message));
                    EMLog.e("createChatThread","onSuccess");
                }
            }

            @Override
            public void onError(int error, String errorMsg) {
                if(isActive()) {
                    runOnUI(()->mView.onCreateThreadFail(error, errorMsg));
                    EMLog.e("createChatThread","onError: " + error + "  " + errorMsg);
                }
            }
        });
    }

    @Override
    public void sendMessage(EMMessage message) {
        if(message == null) {
            if(isActive()) {
                runOnUI(() -> mView.sendMessageFail("message is null!"));
            }
            return;
        }
        if(TextUtils.isEmpty(message.getTo())) {
            message.setTo(toChatUsername);
        }
        addMessageAttributes(message);
        if (chatType == EaseChatType.GROUP_CHAT){
            message.setChatType(EMMessage.ChatType.GroupChat);
        }else if(chatType == EaseChatType.CHATROOM){
            message.setChatType(EMMessage.ChatType.ChatRoom);
        }
        // Add thread label for message
        message.setIsChatThreadMessage(true);
        message.setMessageStatusCallback(new EMCallBack() {
            @Override
            public void onSuccess() {
                if(isActive()) {
                    runOnUI(()-> mView.onPresenterMessageSuccess(message));
                }
            }

            @Override
            public void onError(int code, String error) {
                if(isActive()) {
                    runOnUI(()-> mView.onPresenterMessageError(message, code, error));
                }
            }

            @Override
            public void onProgress(int progress, String status) {
                if(isActive()) {
                    runOnUI(()-> mView.onPresenterMessageInProgress(message, progress));
                }
            }
        });
        // send message
        EMClient.getInstance().chatManager().sendMessage(message);
        if(isActive()) {
            runOnUI(()-> mView.sendMessageFinish(message));
        }
    }

    public void setMessage(EMMessage message) {
        createThread(etInput == null ? "" : etInput.getText().toString().trim(), message);
    }
}
