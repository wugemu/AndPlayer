package com.lhp.playdemo;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.alivc.player.AliVcMediaPlayer;
import com.alivc.player.MediaPlayer;
import com.aliyun.vodplayerview.utils.NetWatchdog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.ui.widget.DanmakuView;

public class BasePlayActivity extends AppCompatActivity {

    private PlayFragment mPlayFragment;
    private ViewPager mViewPager;
    private List<Fragment> mFragmentList = new ArrayList<>();
    private FragmentAdapter mFragmentAdapter;
    private SurfaceView mSurfaceView;
    private AliVcMediaPlayer mPlayer;
    private boolean mMute = false;
    private String mUrl = "rtmp://10.42.0.1/videotest";
    private NetWatchdog netWatchdog;
    private DanmakuView mDanmakuView;
    private DanmakuContext mDanmakuContext;
    private boolean mIsShowDanmaku;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base_play);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
                holder.setKeepScreenOn(true);
//                Log.d(TAG, "AlivcPlayer onSurfaceCreated." + mPlayer);

                // Important: surfaceView changed from background to front, we need reset surface to mediaplayer.
                // 对于从后台切换到前台,需要重设surface;部分手机锁屏也会做前后台切换的处理
                if (mPlayer != null) {
                    mPlayer.setVideoSurface(mSurfaceView.getHolder().getSurface());
                }

//                Log.d(TAG, "AlivcPlayeron SurfaceCreated over.");
            }

            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
//                Log.d(TAG, "onSurfaceChanged is valid ? " + holder.getSurface().isValid());
                if (mPlayer != null)
                    mPlayer.setSurfaceChanged();
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
//                Log.d(TAG, "onSurfaceDestroy.");
            }
        });

        mPlayFragment=new PlayFragment();
        initViewPager();
        initVodPlayer();
        setMaxBufferDuration();
        replay();
        if (mMute) {
            mPlayer.setMuteMode(mMute);
        }
        mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        mPlayer.setRenderRotate(MediaPlayer.VideoRotate.ROTATE_0);
        mPlayer.setRenderMirrorMode(MediaPlayer.VideoMirrorMode.VIDEO_MIRROR_MODE_NONE);

//        setPlaySource();
        netWatchdog = new NetWatchdog(this);
        netWatchdog.setNetChangeListener(new NetWatchdog.NetChangeListener() {
            @Override
            public void onWifiTo4G() {
                if (mPlayer.isPlaying()) {
                    pause();
                }
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(BasePlayActivity.this);
                alertDialog.setTitle(getString(R.string.net_change_to_4g));
                alertDialog.setMessage(getString(R.string.net_change_to_continue));
                alertDialog.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        replay();
                    }
                });
                alertDialog.setNegativeButton(getString(R.string.no), null);
                AlertDialog alert = alertDialog.create();
                alert.show();

//                Toast.makeText(LiveModeActivity.this.getApplicationContext(), R.string.net_change_to_4g, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void on4GToWifi() {
//                Toast.makeText(LiveModeActivity.this.getApplicationContext(), R.string.net_change_to_wifi, Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onNetDisconnected() {
//                Toast.makeText(BasePlayActivity.this.getApplicationContext(), R.string.net_disconnect, Toast.LENGTH_SHORT).show();
            }
        });
        netWatchdog.startWatch();

        mDanmakuView = (DanmakuView) findViewById(R.id.danmaku_view);
        mDanmakuView.enableDanmakuDrawingCache(true);
        mDanmakuView.setCallback(new DrawHandler.Callback() {
            @Override
            public void prepared() {
                mIsShowDanmaku = true;
                mDanmakuView.start();
                generateSomeDanmaku();
            }

            @Override
            public void updateTimer(DanmakuTimer timer) {

            }

            @Override
            public void danmakuShown(BaseDanmaku danmaku) {

            }

            @Override
            public void drawingFinished() {

            }
        });

        mDanmakuContext = DanmakuContext.create();
        mDanmakuView.prepare(parser, mDanmakuContext);

        final LinearLayout operationLayout = (LinearLayout) findViewById(R.id.operation_layout);
        final Button send = (Button) findViewById(R.id.send);
        final EditText editText = (EditText) findViewById(R.id.edit_text);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String content = editText.getText().toString();
                if (!TextUtils.isEmpty(content)) {
                    addDanmaku(content, true);
                    editText.setText("");
                }
            }
        });
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener (new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (visibility == View.SYSTEM_UI_FLAG_VISIBLE) {
                    onWindowFocusChanged(true);
                }
            }
        });

    }
    private BaseDanmakuParser parser = new BaseDanmakuParser() {
        @Override
        protected IDanmakus parse() {
            return new Danmakus();
        }
    };
    private void initViewPager() {
        mViewPager = (ViewPager) findViewById(R.id.tv_pager);
//        mFragmentList.add(mPushTextStatsFragment);
        mFragmentList.add(mPlayFragment);
//        mFragmentList.add(mPushDiagramStatsFragment);
        mFragmentAdapter = new FragmentAdapter(this.getSupportFragmentManager(), mFragmentList) ;
        mViewPager.setAdapter(mFragmentAdapter);

    }
    private void initVodPlayer() {
        mPlayer = new AliVcMediaPlayer(this, mSurfaceView);

//        mPlayer.setPreparedListener(new MyPreparedListener(this));
//        mPlayer.setFrameInfoListener(new MyFrameInfoListener(this));
//        mPlayer.setErrorListener(new MyErrorListener(this));
//        mPlayer.setCompletedListener(new MyPlayerCompletedListener(this));
//        mPlayer.setSeekCompleteListener(new MySeekCompleteListener(this));
//        mPlayer.setStoppedListener(new MyStoppedListener(this));
        mPlayer.enableNativeLog();
    }
    private void setMaxBufferDuration() {
//        String maxBufferDurationStr = maxBufDurationEdit.getText().toString();
        int maxBD = 8000;
//        try {
//            maxBD = Integer.valueOf(maxBufferDurationStr);
//
//        } catch (Exception e) {
//            maxBufDurationEdit.setText("0");
//        }
//        if (maxBD < 0) {
//            Toast.makeText(getApplicationContext(), R.string.max_buffer_duration_nagtive, Toast.LENGTH_SHORT).show();
//            return;
//        }
        if (mPlayer != null) {
            mPlayer.setMaxBufferDuration(maxBD);
        }
    }
    public class FragmentAdapter extends FragmentPagerAdapter {

        List<Fragment> fragmentList = new ArrayList<>();
        public FragmentAdapter(FragmentManager fm, List<Fragment> fragmentList) {
            super(fm);
            this.fragmentList = fragmentList;
        }

        @Override
        public Fragment getItem(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void start() {

        if (mPlayer != null) {
            mPlayer.prepareAndPlay(mUrl);
        }
    }

    private void pause() {
        if (mPlayer != null) {
            mPlayer.pause();
        }
    }

    private void stop() {
        if (mPlayer != null) {
            mPlayer.stop();
        }
    }

    private void resume() {
        if (mPlayer != null) {
            mPlayer.play();
        }
    }

    private void destroy() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.destroy();
        }
    }

    private void replay() {
        stop();
        start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resume();
        if (mDanmakuView != null && mDanmakuView.isPrepared() && mDanmakuView.isPaused()) {
            mDanmakuView.resume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //when view goto background,will pausethe player, so we save the player's status here,
        // and when activity resumed, we resume the player's status.
        savePlayerState();
    }

    private void savePlayerState() {
        if (mPlayer.isPlaying()) {
            //we pause the player for not playing on the background
            // 不可见，暂停播放器
            pause();
        }
    }

    @Override
    protected void onDestroy() {
        stop();
        destroy();
        netWatchdog.stopWatch();
        super.onDestroy();
        mIsShowDanmaku = false;
        if (mDanmakuView != null) {
            mDanmakuView.release();
            mDanmakuView = null;
        }
    }

    /**
     * 向弹幕View中添加一条弹幕
     * @param content       弹幕的具体内容
     * @param  withBorder   弹幕是否有边框
     */
    private void addDanmaku(String content, boolean withBorder) {
        BaseDanmaku danmaku = mDanmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
        danmaku.text = content;
        danmaku.padding = 5;
        danmaku.textSize = sp2px(20);
        danmaku.textColor = Color.WHITE;
        danmaku.setTime(mDanmakuView.getCurrentTime());
        if (withBorder) {
            danmaku.borderColor = Color.GREEN;
        }
        mDanmakuView.addDanmaku(danmaku);
    }

    /**
     * 随机生成一些弹幕内容以供测试
     */
    private void generateSomeDanmaku() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(mIsShowDanmaku) {
                    int time = new Random().nextInt(300);
                    String content = "" + time + time;
                    addDanmaku(content, false);
                    try {
                        Thread.sleep(time);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    /**
     * sp转px的方法。
     */
    public int sp2px(float spValue) {
        final float fontScale = getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
        }
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }


}
