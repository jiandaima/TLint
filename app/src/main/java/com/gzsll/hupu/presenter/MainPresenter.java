package com.gzsll.hupu.presenter;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.gzsll.hupu.AppManager;
import com.gzsll.hupu.UpdateAgent;
import com.gzsll.hupu.api.forum.ForumApi;
import com.gzsll.hupu.api.game.GameApi;
import com.gzsll.hupu.bean.MessageData;
import com.gzsll.hupu.bean.Pm;
import com.gzsll.hupu.bean.PmData;
import com.gzsll.hupu.components.storage.UserStorage;
import com.gzsll.hupu.db.User;
import com.gzsll.hupu.db.UserDao;
import com.gzsll.hupu.helper.SettingPrefHelper;
import com.gzsll.hupu.helper.ToastHelper;
import com.gzsll.hupu.otto.AccountChangeEvent;
import com.gzsll.hupu.otto.ChangeThemeEvent;
import com.gzsll.hupu.otto.LoginSuccessEvent;
import com.gzsll.hupu.otto.MessageReadEvent;
import com.gzsll.hupu.ui.activity.AccountActivity;
import com.gzsll.hupu.ui.activity.LoginActivity;
import com.gzsll.hupu.ui.activity.MessageActivity;
import com.gzsll.hupu.ui.activity.UserProfileActivity;
import com.gzsll.hupu.ui.view.MainView;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

/**
 * Created by sll on 2016/3/17.
 */
public class MainPresenter extends Presenter<MainView> {


    @Inject
    SettingPrefHelper mSettingPrefHelper;
    @Inject
    UpdateAgent mUpdateAgent;
    @Inject
    UserStorage mUserStorage;
    @Inject
    UserDao mUserDao;
    @Inject
    Bus mBus;
    @Inject
    ForumApi mForumApi;
    @Inject
    ToastHelper mToastHelper;
    @Inject
    Activity mActivity;
    @Inject
    GameApi mGameApi;

    private int count = 0;

    @Inject
    @Singleton
    public MainPresenter() {
    }


    @Override
    public void attachView(@NonNull MainView view) {
        super.attachView(view);
        mBus.register(this);
        initUserInfo();
        initNotification();
    }

    private void initUserInfo() {
        view.renderUserInfo(isLogin() ? mUserStorage.getUser() : null);
    }


    private void initNotification() {
        if (isLogin()) {
            Observable.zip(mGameApi.queryPmList(""), mForumApi.getMessageList("", 1), new Func2<PmData, MessageData, Integer>() {
                @Override
                public Integer call(PmData pmData, MessageData messageData) {
                    int size = 0;
                    if (pmData != null) {
                        for (Pm pm : pmData.result.data) {
                            if (!TextUtils.isEmpty(pm.unread) && pm.unread.equals("1")) {
                                size++;
                            }
                        }
                    }

                    if (messageData != null && messageData.status == 200) {
                        size += messageData.result.list.size();
                    }
                    return size;
                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Integer>() {
                @Override
                public void call(Integer integer) {
                    count = integer;
                    view.renderNotification(integer);
                }
            }, new Action1<Throwable>() {
                @Override
                public void call(Throwable throwable) {

                }
            });
        }
    }

    public void clickNotification() {
        if (isLogin()) {
            MessageActivity.startActivity(mActivity);
        } else {
            login();
        }
    }

    public void login() {
        LoginActivity.startActivity(mActivity);
        mToastHelper.showToast("请先登录");
    }

    public void clickCover() {
        if (isLogin()) {
            UserProfileActivity.startActivity(mActivity, mUserStorage.getUid());
        } else {
            login();
        }
    }


    public void showAccountMenu() {
        Observable.create(new Observable.OnSubscribe<List<User>>() {
            @Override
            public void call(Subscriber<? super List<User>> subscriber) {
                final List<User> userList = mUserDao.queryBuilder().list();
                for (User bean : userList) {
                    if (bean.getUid().equals(mUserStorage.getUid())) {
                        userList.remove(bean);
                        break;
                    }
                }
                subscriber.onNext(userList);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<List<User>>() {
            @Override
            public void call(List<User> users) {
                final String[] items = new String[users.size() + 1];
                for (int i = 0; i < users.size(); i++)
                    items[i] = users.get(i).getUserName();
                items[items.length - 1] = "账号管理";
                view.renderAccountList(users, items);

            }
        });
    }

    public void onAccountItemClick(int position, final List<User> users, final String[] items) {
        if (position == items.length - 1) {
            // 账号管理
            AccountActivity.startActivity(mActivity);
        } else {
            mUserStorage.login(users.get(position));
            initUserInfo();
        }
    }


    public void exist() {
        if (isCanExit()) {
            AppManager.getAppManager().AppExit(mActivity);
        }
    }

    private long mExitTime = 0;

    private boolean isCanExit() {
        if (System.currentTimeMillis() - mExitTime > 2000) {
            mToastHelper.showToast("再按一次退出程序");
            mExitTime = System.currentTimeMillis();
            return false;
        }
        return true;
    }

    public boolean isLogin() {
        return mUserStorage.isLogin();
    }

    @Override
    public void detachView() {
        mBus.unregister(this);
        count = 0;
    }

    @Subscribe
    public void onChangeThemeEvent(ChangeThemeEvent event) {
        view.reload();
    }


    @Subscribe
    public void onLoginSuccessEvent(LoginSuccessEvent event) {
        initUserInfo();
    }

    @Subscribe
    public void onAccountChangeEvent(AccountChangeEvent event) {
        initUserInfo();
    }

    @Subscribe
    public void onMessageReadEvent(MessageReadEvent event) {
        if (count >= 1) {
            count--;
        }
        view.renderNotification(count);
    }
}
