package android.app.src.main.java.com.jinxian.flutter_tencentplayer;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.LongSparseArray;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.NonNull;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.TextureRegistry;


import com.tencent.rtmp.ITXVodPlayListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXPlayerAuthBuilder;
import com.tencent.rtmp.TXVodPlayConfig;
import com.tencent.rtmp.TXVodPlayer;
import com.tencent.rtmp.downloader.ITXVodDownloadListener;
import com.tencent.rtmp.downloader.TXVodDownloadDataSource;
import com.tencent.rtmp.downloader.TXVodDownloadManager;
import com.tencent.rtmp.downloader.TXVodDownloadMediaInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;


/**
 * FlutterTencentplayerPlugin
 */
public class FlutterTencentplayerPlugin implements FlutterPlugin,MethodChannel.MethodCallHandler, ActivityAware {
    private static final String CHANNEL_NAME = "flutter_tencentplayer";
    private  TextureRegistry mTextureRegistry;
    private  MethodChannel methodChannel;
    private BinaryMessenger mBinaryMessenger;
    private WeakReference<Activity> currentActivity;

    /** Plugin registration. */
    public static void registerWith(PluginRegistry.Registrar registrar) {
        FlutterTencentplayerPlugin flutterLivePlugin = new FlutterTencentplayerPlugin();
        flutterLivePlugin.setupChannel(registrar.messenger(),registrar.textures());
        registrar.addViewDestroyListener(flutterNativeView -> {
                    flutterLivePlugin.onDestroy();
                    return false;
                }
        );
    }
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        currentActivity = new WeakReference<>(binding.getActivity());
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        currentActivity = null;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        setupChannel(binding.getBinaryMessenger(),binding.getTextureRegistry());
    }
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        teardownChannel();
    }

    private void setupChannel(BinaryMessenger binaryMessenger, TextureRegistry textureRegistry) {
        mTextureRegistry=textureRegistry;
        mBinaryMessenger=binaryMessenger;
        methodChannel = new MethodChannel(binaryMessenger, CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);
        videoPlayers = new LongSparseArray<>();
        downloadManagerMap = new HashMap<>();

    }

    private void teardownChannel() {
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;
        mTextureRegistry=null;
        mBinaryMessenger=null;
        if(videoPlayers!=null){
            videoPlayers.clear();
        }
        if(downloadManagerMap!=null){
             downloadManagerMap.clear();
        }
        videoPlayers=null;
        downloadManagerMap=null;
    }


    private  LongSparseArray<TencentPlayer> videoPlayers;
    private  HashMap<String, TencentDownload> downloadManagerMap;




    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if(mBinaryMessenger==null||mTextureRegistry==null||currentActivity==null||currentActivity.get()==null){
            return;
        }
        TextureRegistry textures = mTextureRegistry;
        if ("getPlatformVersion".equals(call.method)) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        }

        switch (call.method) {
            case "init":
                disposeAllPlayers();
                break;
            case "create":
                TextureRegistry.SurfaceTextureEntry handle = textures.createSurfaceTexture();
                EventChannel eventChannel = new EventChannel(mBinaryMessenger, "flutter_tencentplayer/videoEvents" + handle.id());
                TencentPlayer player = new TencentPlayer(currentActivity.get(),eventChannel, handle, call, result);
                videoPlayers.put(handle.id(), player);
                break;
            case "download":
                String urlOrFileId = call.argument("urlOrFileId").toString();
                EventChannel downloadEventChannel = new EventChannel(mBinaryMessenger, "flutter_tencentplayer/downloadEvents" + urlOrFileId);
                TencentDownload tencentDownload = new TencentDownload(downloadEventChannel, call, result);

                downloadManagerMap.put(urlOrFileId, tencentDownload);
                break;
            case "stopDownload":
                downloadManagerMap.get(call.argument("urlOrFileId").toString()).stopDownload();
                result.success(null);
                break;
            default:
                long textureId = ((Number) call.argument("textureId")).longValue();
                TencentPlayer tencentPlayer = videoPlayers.get(textureId);
                if (tencentPlayer == null) {
                    result.error(
                            "Unknown textureId",
                            "No video player associated with texture id " + textureId,
                            null);
                    return;
                }
                onMethodCall(call, result, textureId, tencentPlayer);
                break;

        }
    }

    // flutter 发往android的命令
    private void onMethodCall(MethodCall call, MethodChannel.Result result, long textureId, TencentPlayer player) {
        switch (call.method) {
            case "play":
                player.play();
                result.success(null);
                break;
            case "pause":
                player.pause();
                result.success(null);
                break;
            case "seekTo":
                int location = ((Number) call.argument("location")).intValue();
                player.seekTo(location);
                result.success(null);
                break;
            case "setRate":
                float rate = ((Number) call.argument("rate")).floatValue();
                player.setRate(rate);
                result.success(null);
                break;
            case "setBitrateIndex":
                int bitrateIndex = ((Number) call.argument("index")).intValue();
                player.setBitrateIndex(bitrateIndex);
                result.success(null);
                break;
            case "dispose":
                player.dispose();
                videoPlayers.remove(textureId);
                result.success(null);
                break;
            default:
                result.notImplemented();
                break;
        }

    }


    private void disposeAllPlayers() {
        for (int i = 0; i < videoPlayers.size(); i++) {
            videoPlayers.valueAt(i).dispose();
        }
        videoPlayers.clear();
    }

    private void onDestroy() {
        disposeAllPlayers();
    }



    ///////////////////// TencentPlayer 开始////////////////////

    private static class TencentPlayer implements ITXVodPlayListener {
        private TXVodPlayer mVodPlayer;
        TXVodPlayConfig mPlayConfig;
        private Surface surface;
        TXPlayerAuthBuilder authBuilder;

        private final TextureRegistry.SurfaceTextureEntry textureEntry;

        private TencentQueuingEventSink eventSink = new TencentQueuingEventSink();

        private final EventChannel eventChannel;
        private final Context mRegistrarContext;

        private OrientationEventListener orientationEventListener;


        TencentPlayer(
                Context mRegistrarContext,
                EventChannel eventChannel,
                TextureRegistry.SurfaceTextureEntry textureEntry,
                MethodCall call,
                MethodChannel.Result result) {
            this.mRegistrarContext = mRegistrarContext;
            this.eventChannel = eventChannel;
            this.textureEntry = textureEntry;


            mVodPlayer = new TXVodPlayer(mRegistrarContext);

            setPlayConfig(call);

            setTencentPlayer(call);

            setFlutterBridge(eventChannel, textureEntry, result);

            setPlaySource(call);

            setOrientationEventListener();

        }

        private void setOrientationEventListener() {
            orientationEventListener = new OrientationEventListener(mRegistrarContext) {
                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                        return;  //手机平放时，检测不到有效的角度
                    }
                    Map<String, Object> orientationMap = new HashMap<>();
                    orientationMap.put("event", "orientation");
                    orientationMap.put("orientation", orientation);
                    eventSink.success(orientationMap);
                }
            };
            orientationEventListener.enable();
        }


        private void setPlayConfig(MethodCall call) {
            mPlayConfig = new TXVodPlayConfig();
            if (call.argument("cachePath") != null) {
                mPlayConfig.setCacheFolderPath(call.argument("cachePath").toString());//        mPlayConfig.setCacheFolderPath(Environment.getExternalStorageDirectory().getPath() + "/nellcache");
                mPlayConfig.setMaxCacheItems(1);
            } else {
                mPlayConfig.setCacheFolderPath(null);
                mPlayConfig.setMaxCacheItems(0);
            }
            if (call.argument("headers") != null) {
                mPlayConfig.setHeaders((Map<String, String>) call.argument("headers"));
            }

            mPlayConfig.setProgressInterval(((Number) call.argument("progressInterval")).intValue());
            mVodPlayer.setConfig(this.mPlayConfig);
        }

        private  void setTencentPlayer(MethodCall call) {
            mVodPlayer.setVodListener(this);
//            mVodPlayer.enableHardwareDecode(true);
            mVodPlayer.setLoop((boolean) call.argument("loop"));
            if (call.argument("startTime") != null) {
                mVodPlayer.setStartTime(((Number)call.argument("startTime")).floatValue());
            }
            mVodPlayer.setAutoPlay((boolean) call.argument("autoPlay"));

        }

        private void setFlutterBridge(EventChannel eventChannel, TextureRegistry.SurfaceTextureEntry textureEntry, MethodChannel.Result result) {
            // 注册android向flutter发事件
            eventChannel.setStreamHandler(
                    new EventChannel.StreamHandler() {
                        @Override
                        public void onListen(Object o, EventChannel.EventSink sink) {
                            eventSink.setDelegate(sink);
                        }

                        @Override
                        public void onCancel(Object o) {
                            eventSink.setDelegate(null);
                        }
                    }
            );

            surface = new Surface(textureEntry.surfaceTexture());
            mVodPlayer.setSurface(surface);


            Map<String, Object> reply = new HashMap<>();
            reply.put("textureId", textureEntry.id());
            result.success(reply);
        }

        private void setPlaySource(MethodCall call) {
            // network FileId播放
            if (call.argument("auth") != null) {
                authBuilder = new TXPlayerAuthBuilder();
                Map<String, Object> authMap = call.argument("auth");
                int appId=0;
                if(authMap!=null){
                    try {
                        String appIdStr=authMap.get("appId").toString();
                        appId=Integer.parseInt(appIdStr);
                    }catch (Exception ignore){
                    }
                    authBuilder.setAppId(appId);
                    authBuilder.setFileId(authMap.get("fileId").toString());
                }
                mVodPlayer.startPlay(authBuilder);
            } else {
                // asset播放
                if (call.argument("asset") != null) {
                    String assetLookupKey = FlutterInjector.instance().flutterLoader().getLookupKeyForAsset(call.argument("asset").toString());
                    AssetManager assetManager = mRegistrarContext.getAssets();
                    try {
                        InputStream inputStream = assetManager.open(assetLookupKey);
                        String cacheDir = mRegistrarContext.getCacheDir().getAbsoluteFile().getPath();
                        String fileName = Base64.encodeToString(assetLookupKey.getBytes(), Base64.DEFAULT);
                        File file = new File(cacheDir, fileName + ".mp4");
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        if(!file.exists()){
                            file.createNewFile();
                        }
                        int ch = 0;
                        while((ch=inputStream.read()) != -1) {
                            fileOutputStream.write(ch);
                        }
                        inputStream.close();
                        fileOutputStream.close();

                        mVodPlayer.startPlay(file.getPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    // file、 network播放
                    mVodPlayer.startPlay(call.argument("uri").toString());
                }
            }
        }

        // 播放器监听1
        @Override
        public void onPlayEvent(TXVodPlayer player, int event, Bundle param) {
            switch (event) {
                //准备阶段
                case TXLiveConstants.PLAY_EVT_VOD_PLAY_PREPARED:
                    Map<String, Object> preparedMap = new HashMap<>();
                    preparedMap.put("event", "initialized");
                    preparedMap.put("duration", (int) player.getDuration());
                    preparedMap.put("width", player.getWidth());
                    preparedMap.put("height", player.getHeight());
                    eventSink.success(preparedMap);
                    break;
                case TXLiveConstants.PLAY_EVT_PLAY_PROGRESS:
                    Map<String, Object> progressMap = new HashMap<>();
                    progressMap.put("event", "progress");
                    progressMap.put("progress", param.getInt(TXLiveConstants.EVT_PLAY_PROGRESS_MS));
                    progressMap.put("duration", param.getInt(TXLiveConstants.EVT_PLAY_DURATION_MS));
                    progressMap.put("playable", param.getInt(TXLiveConstants.EVT_PLAYABLE_DURATION_MS));
                    eventSink.success(progressMap);
                    break;
                case TXLiveConstants.PLAY_EVT_PLAY_LOADING:
                    Map<String, Object> loadingMap = new HashMap<>();
                    loadingMap.put("event", "loading");
                    eventSink.success(loadingMap);
                    break;
                case TXLiveConstants.PLAY_EVT_VOD_LOADING_END:
                    Map<String, Object> loadingendMap = new HashMap<>();
                    loadingendMap.put("event", "loadingend");
                    eventSink.success(loadingendMap);
                    break;
                case TXLiveConstants.PLAY_EVT_PLAY_END:
                    Map<String, Object> playendMap = new HashMap<>();
                    playendMap.put("event", "playend");
                    eventSink.success(playendMap);
                    break;
                case TXLiveConstants.PLAY_ERR_NET_DISCONNECT:
                    Map<String, Object> disconnectMap = new HashMap<>();
                    disconnectMap.put("event", "disconnect");
                    if (mVodPlayer != null) {
                        mVodPlayer.setVodListener(null);
                        mVodPlayer.stopPlay(true);
                    }
                    eventSink.success(disconnectMap);
                    break;
            }
            if (event < 0) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("event", "error");
                errorMap.put("errorInfo", param.getString(TXLiveConstants.EVT_DESCRIPTION));
                eventSink.success(errorMap);
            }
        }

        // 播放器监听2
        @Override
        public void onNetStatus(TXVodPlayer txVodPlayer, Bundle param) {
            Map<String, Object> netStatusMap = new HashMap<>();
            netStatusMap.put("event", "netStatus");
            netStatusMap.put("netSpeed", param.getInt(TXLiveConstants.NET_STATUS_NET_SPEED));
            netStatusMap.put("cacheSize", param.getInt(TXLiveConstants.NET_STATUS_V_SUM_CACHE_SIZE));
            eventSink.success(netStatusMap);
        }

        void play() {
            if (!mVodPlayer.isPlaying()) {
                mVodPlayer.resume();
            }
        }

        void pause() {
            mVodPlayer.pause();
        }

        void seekTo(int location) {
            mVodPlayer.seek(location);
        }

        void setRate(float rate) {
            mVodPlayer.setRate(rate);
        }

        void setBitrateIndex(int index) {
            mVodPlayer.setBitrateIndex(index);
        }

        void dispose() {
            if (mVodPlayer != null) {
                mVodPlayer.setVodListener(null);
                mVodPlayer.stopPlay(true);
            }
            textureEntry.release();
            eventChannel.setStreamHandler(null);
            if (surface != null) {
                surface.release();
            }
            orientationEventListener.disable();
        }
    }
    ///////////////////// TencentPlayer 结束////////////////////

    ////////////////////  TencentDownload 开始/////////////////
    class TencentDownload implements ITXVodDownloadListener {
        private TencentQueuingEventSink eventSink = new TencentQueuingEventSink();

        private String fileId;

        private TXVodDownloadManager downloader;

        private TXVodDownloadMediaInfo txVodDownloadMediaInfo;


        void stopDownload() {
            if (downloader != null && txVodDownloadMediaInfo != null) {
                downloader.stopDownload(txVodDownloadMediaInfo);
            }
        }


        TencentDownload(
                EventChannel eventChannel,
                MethodCall call,
                MethodChannel.Result result) {
            downloader = TXVodDownloadManager.getInstance();
            downloader.setListener(this);
            downloader.setDownloadPath(call.argument("savePath").toString());
            String urlOrFileId = call.argument("urlOrFileId").toString();

            if (urlOrFileId.startsWith("http")) {
                txVodDownloadMediaInfo = downloader.startDownloadUrl(urlOrFileId);
            } else {
                TXPlayerAuthBuilder auth = new TXPlayerAuthBuilder();
                int appId=0;
                try {
                    String appIdStr=call.argument("appId").toString();
                    appId=Integer.parseInt(appIdStr);
                }catch (Exception e){
                }
                auth.setAppId(appId);
                auth.setFileId(urlOrFileId);
                int quanlity = ((Number)call.argument("quanlity")).intValue();
                String templateName = "HLS-标清-SD";
                if (quanlity == 2) {
                    templateName = "HLS-标清-SD";
                } else if (quanlity == 3) {
                    templateName = "HLS-高清-HD";
                } else if (quanlity == 4) {
                    templateName = "HLS-全高清-FHD";
                }
                TXVodDownloadDataSource source = new TXVodDownloadDataSource(auth, templateName);
                txVodDownloadMediaInfo = downloader.startDownload(source);
            }

            eventChannel.setStreamHandler(
                    new EventChannel.StreamHandler() {
                        @Override
                        public void onListen(Object o, EventChannel.EventSink sink) {
                            eventSink.setDelegate(sink);
                        }

                        @Override
                        public void onCancel(Object o) {
                            eventSink.setDelegate(null);
                        }
                    }
            );
            result.success(null);
        }

        @Override
        public void onDownloadStart(TXVodDownloadMediaInfo txVodDownloadMediaInfo) {
            dealCallToFlutterData("start", txVodDownloadMediaInfo);

        }

        @Override
        public void onDownloadProgress(TXVodDownloadMediaInfo txVodDownloadMediaInfo) {
            dealCallToFlutterData("progress", txVodDownloadMediaInfo);

        }

        @Override
        public void onDownloadStop(TXVodDownloadMediaInfo txVodDownloadMediaInfo) {
            dealCallToFlutterData("stop", txVodDownloadMediaInfo);
        }

        @Override
        public void onDownloadFinish(TXVodDownloadMediaInfo txVodDownloadMediaInfo) {
            dealCallToFlutterData("complete", txVodDownloadMediaInfo);
        }

        @Override
        public void onDownloadError(TXVodDownloadMediaInfo txVodDownloadMediaInfo, int i, String s) {
            HashMap<String, Object> targetMap = Util.convertToMap(txVodDownloadMediaInfo);
            targetMap.put("downloadStatus", "error");
            targetMap.put("error", "code:" + i + "  msg:" +  s);
            if (txVodDownloadMediaInfo.getDataSource() != null) {
                targetMap.put("quanlity", txVodDownloadMediaInfo.getDataSource().getQuality());
                targetMap.putAll(Util.convertToMap(txVodDownloadMediaInfo.getDataSource().getAuthBuilder()));
            }
            eventSink.success(targetMap);
        }

        @Override
        public int hlsKeyVerify(TXVodDownloadMediaInfo txVodDownloadMediaInfo, String s, byte[] bytes) {
            return 0;
        }

        private void dealCallToFlutterData(String type, TXVodDownloadMediaInfo txVodDownloadMediaInfo) {
            HashMap<String, Object> targetMap = Util.convertToMap(txVodDownloadMediaInfo);
            targetMap.put("downloadStatus", type);
            if (txVodDownloadMediaInfo.getDataSource() != null) {
                targetMap.put("quanlity", txVodDownloadMediaInfo.getDataSource().getQuality());
                targetMap.putAll(Util.convertToMap(txVodDownloadMediaInfo.getDataSource().getAuthBuilder()));
            }
            eventSink.success(targetMap);
        }


    }
    ////////////////////  TencentDownload 结束/////////////////
}
