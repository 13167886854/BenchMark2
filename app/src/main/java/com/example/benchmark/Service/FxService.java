package com.example.benchmark.Service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.benchmark.R;
import com.example.benchmark.utils.ApkUtil;
import com.example.benchmark.utils.CacheConst;
import com.example.benchmark.utils.CacheUtil;
import com.example.benchmark.utils.CodeUtils;
import com.example.benchmark.utils.ScoreUtil;
import com.example.benchmark.utils.ServiceUtil;

import java.nio.ByteBuffer;
import java.util.Objects;

public class FxService extends Service {
    private final int screenHeight = CacheUtil.getInt(CacheConst.KEY_SCREEN_HEIGHT);
    private final int screenWidth = CacheUtil.getInt(CacheConst.KEY_SCREEN_WIDTH);
    private final int screenDpi = CacheUtil.getInt(CacheConst.KEY_SCREEN_DPI);

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mediaProjection;


    private
    //定义浮动窗口布局
    LinearLayout mFloatLayout;
    LayoutParams wmParams;
    //创建浮动窗口设置布局参数的对象
    WindowManager mWindowManager;
    private Context mContext;
    TextView mFloatView;
    private long startTime;
    private long endTime;
    private int statusBarHeight;

    private static final String TAG = "FxService";

    @Override
    public void onCreate()
    {
        super.onCreate();
        mContext=FxService.this;
        createFloatView();

    }
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createFloatView()
    {
        wmParams = new LayoutParams();
        //获取WindowManagerImpl.CompatModeWrapper
        mWindowManager =  (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        //设置window type
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.O){
            wmParams.type = LayoutParams.TYPE_APPLICATION_OVERLAY;
        }else{
            wmParams.type= LayoutParams.TYPE_TOAST;
        }
        //设置图片格式，效果为背景透明
        wmParams.format = PixelFormat.RGBA_8888;
        //设置浮动窗口不可聚焦（实现操作除浮动窗口外的其他可见窗口的操作）
        wmParams.flags =
//          LayoutParams.FLAG_NOT_TOUCH_MODAL |
                LayoutParams.FLAG_NOT_FOCUSABLE
//          LayoutParams.FLAG_NOT_TOUCHABLE
        ;
        //调整悬浮窗显示的停靠位置为左侧置顶
        wmParams.gravity = Gravity.START | Gravity.TOP;
        // 以屏幕左上角为原点，设置x、y初始值(设置最大直接显示在右下角)
        wmParams.x = screenWidth - 50;
        wmParams.y = screenHeight / 2;
        //设置悬浮窗口长宽数据
        wmParams.width = LayoutParams.WRAP_CONTENT;
        wmParams.height = LayoutParams.WRAP_CONTENT;
        LayoutInflater inflater = LayoutInflater.from(getApplication());
        //获取浮动窗口视图所在布局
        mFloatLayout = (LinearLayout) inflater.inflate(R.layout.float_layout, null);
        mFloatView = (TextView)mFloatLayout.findViewById(R.id.textinfo);
        mWindowManager.addView(mFloatLayout, wmParams);

        //获取状态栏的高度
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        statusBarHeight = getResources().getDimensionPixelSize(resourceId);

        // handler.sendEmptyMessage(1);
        //浮动窗口按钮
        mFloatLayout.measure(View.MeasureSpec.makeMeasureSpec(0,
                View.MeasureSpec.UNSPECIFIED), View.MeasureSpec
                .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        //设置监听浮动窗口的触摸移动
        mFloatView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean isclick=false;
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        startTime=System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        //getRawX是触摸位置相对于屏幕的坐标，getX是相对于按钮的坐标
                        wmParams.x = (int) event.getRawX() - mFloatView.getMeasuredWidth()/2;
                        wmParams.y = (int) event.getRawY() - mFloatView.getMeasuredHeight()/2-statusBarHeight;
                        //Log.d("TWT", "onTouch: "+MainActivity.);
                        //刷新
                        mWindowManager.updateViewLayout(mFloatLayout, wmParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        endTime=System.currentTimeMillis();
                        //小于0.2秒被判断为点击
                        if ((endTime - startTime) > 200) {
                            isclick = false;
                        } else {
                            isclick = true;
                        }
                        break;
                }
                //响应点击事件
                if (isclick) {
                    //Toast.makeText(mContext, "点击了", Toast.LENGTH_SHORT).show();
                    //点击按钮进行截屏bitmap形式
                    Bitmap bitmap = screenShot();
                    String result = CodeUtils.parseCode(bitmap);
                    Log.e("QT-1", result);
                    if ("{}".equals(result)) {
                        // 空数据，点击无效
                        return true;
                    }
                    JSONObject JsonData = JSON.parseObject(result);
                    // 信息获取
                    Log.e("QT-2", JsonData.toJSONString());
                    ScoreUtil.calcAndSaveCPUScores(
                            (String)JsonData.get("cpuName"),
                            getIntDataFromJson(JsonData, "cpuCores")
                    );
                    ScoreUtil.calcAndSaveGPUScores(
                            (String)JsonData.get("gpuVendor"),
                            (String)JsonData.get("gpuRenderer"),
                            (String)JsonData.get("gpuVersion")
                    );
                    ScoreUtil.calcAndSaveRAMScores(
                            getFloatDataFromJson(JsonData, "availRam"),
                            getFloatDataFromJson(JsonData, "totalRam")
                    );
                    ScoreUtil.calcAndSaveROMScores(
                            getFloatDataFromJson(JsonData, "availStorage"),
                            getFloatDataFromJson(JsonData, "totalStorage")
                    );
                    ScoreUtil.calcAndSaveFluencyScores(
                            getFloatDataFromJson(JsonData, "avergeFPS"),
                            getFloatDataFromJson(JsonData, "frameShakingRate"),
                            getFloatDataFromJson(JsonData, "lowFrameRate"),
                            getFloatDataFromJson(JsonData, "frameInterval"),
                            getFloatDataFromJson(JsonData, "jankCount"),
                            getFloatDataFromJson(JsonData, "stutterRate")
                    );
                    ScoreUtil.calcAndSaveTouchScores(
                            getFloatDataFromJson(JsonData, "averageAccuracy"),
                            getFloatDataFromJson(JsonData, "responseTime"),
                            getFloatDataFromJson(JsonData, "averageResponseTime")
                    );
                    if (JsonData.get("resolution") != null) {
                        ScoreUtil.calcAndSaveSoundFrameScores(
                                (String)JsonData.get("resolution"),
                                getFloatDataFromJson(JsonData, "maxdifferencevalue")
                        );
                    }
                    CacheUtil.put(CacheConst.KEY_PERFORMANCE_IS_MONITORED, true);
                    Toast.makeText(FxService.this, "测试结束！", Toast.LENGTH_SHORT).show();
                    ServiceUtil.backToCePingActivity(FxService.this);
                    stopSelf();
                }
                return true;
            }
        });

        mFloatView.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                //Toast.makeText(FxService.this, "onClick", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if(mFloatLayout != null)
        {
            mWindowManager.removeView(mFloatLayout);
        }
        mediaProjection.stop();
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent.getIntExtra("code", -1);
        Intent data = intent.getParcelableExtra("data");
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
        }
        //Bitmap bitmap = screenShot(mediaProjection);
        return super.onStartCommand(intent, flags, startId);
    }

    public Bitmap screenShot(){
        Objects.requireNonNull(mediaProjection);
        @SuppressLint("WrongConstant")
        ImageReader imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 60);
        VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay("screen", screenWidth, screenHeight, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,imageReader.getSurface(), null, null);
        SystemClock.sleep(1000);
        //取最新的图片
        Image image = imageReader.acquireLatestImage();
        // Image image = imageReader.acquireNextImage();
        //释放 virtualDisplay,不释放会报错
        virtualDisplay.release();
        return image2Bitmap(image);
    }

    public static Bitmap image2Bitmap(Image image) {
        if (image == null) {
            System.out.println("image 为空");
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width+ rowPadding / pixelStride , height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        //截取图片
        // Bitmap cutBitmap = Bitmap.createBitmap(bitmap,0,0,width/2,height/2);
        //压缩图片
        // Matrix matrix = new Matrix();
        // matrix.setScale(0.5F, 0.5F);
        // System.out.println(bitmap.isMutable());
        // bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
        image.close();
        return bitmap;
    }

    private float getFloatDataFromJson(JSONObject jsonObject, String name) {
        Object target = jsonObject.getString(name);
        if (target == null) return 0F;
        else return Float.parseFloat(target.toString().split("吉")[0]);
    }

    private int getIntDataFromJson(JSONObject jsonObject, String name) {
        Object target = jsonObject.getString(name);
        if (target == null) return 0;
        else return Integer.parseInt(target.toString());
    }

}


