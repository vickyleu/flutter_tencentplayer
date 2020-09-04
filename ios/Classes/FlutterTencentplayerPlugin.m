#import "FlutterTencentplayerPlugin.h"

#import "FLTTVideoPlayer.h"
#import "FLTTFrameUpdater.h"
#import "FLTTDownLoadManager.h"

@interface FlutterTencentplayerPlugin ()

@property(readonly, nonatomic) NSObject<FlutterTextureRegistry>* registry;
@property(readonly, nonatomic) NSObject<FlutterBinaryMessenger>* messenger;
//@property(readonly, nonatomic) NSMutableDictionary* players;
@property(readonly, nonatomic) NSMutableDictionary* downLoads;
@property(readonly, nonatomic) NSObject<FlutterPluginRegistrar>* registrar;




@end


@implementation FlutterTencentplayerPlugin

NSObject<FlutterPluginRegistrar>* mRegistrar;
FLTTVideoPlayer* player ;

- (instancetype)initWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    self = [super init];
    NSAssert(self, @"super init cannot be nil");
    _registry = [registrar textures];
    _messenger = [registrar messenger];
    _registrar = registrar;
   // _players = [NSMutableDictionary dictionaryWithCapacity:1];
    _downLoads = [NSMutableDictionary dictionaryWithCapacity:1];
     NSLog(@"FLTTVideo  initWithRegistrar");
    return self;
}

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    FlutterMethodChannel* channel = [FlutterMethodChannel
                                     methodChannelWithName:@"flutter_tencentplayer"
                                     binaryMessenger:[registrar messenger]];
//    FlutterTencentplayerPlugin* instance = [[FlutterTencentplayerPlugin alloc] init];
   FlutterTencentplayerPlugin* instance = [[FlutterTencentplayerPlugin alloc] initWithRegistrar:registrar];
    
    [registrar addMethodCallDelegate:instance channel:channel];

   
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
     //NSLog(@"FLTTVideo  call name   %@",call.method);
    if ([@"init" isEqualToString:call.method]) {
        [self disposeAllPlayers];
        result(nil);
    }else if([@"create" isEqualToString:call.method]){
        NSLog(@"FLTTVideo  create");
        [self disposeAllPlayers];
        FLTTFrameUpdater* frameUpdater = [[FLTTFrameUpdater alloc] initWithRegistry:_registry];
//        FLTTVideoPlayer*
        player= [[FLTTVideoPlayer alloc] initWithCall:call frameUpdater:frameUpdater registry:_registry messenger:_messenger];
        if (player) {
            [self onPlayerSetup:player frameUpdater:frameUpdater result:result];
        }
        result(nil);
    }else if([@"download" isEqualToString:call.method]){
        
         NSDictionary* argsMap = call.arguments;
         NSString* urlOrFileId = argsMap[@"urlOrFileId"];
        NSLog(@"下载相关   startdownload  %@", urlOrFileId);
        
        NSString* channelUrl =[NSString stringWithFormat:@"flutter_tencentplayer/downloadEvents%@",urlOrFileId];
        NSLog(@"%@", channelUrl);
        FlutterEventChannel* eventChannel = [FlutterEventChannel
                                             eventChannelWithName:channelUrl
                                             binaryMessenger:_messenger];
       FLTTDownLoadManager* downLoadManager = [[FLTTDownLoadManager alloc] initWithMethodCall:call result:result];
       [eventChannel setStreamHandler:downLoadManager];
       downLoadManager.eventChannel =eventChannel;
       [downLoadManager downLoad];
       
       _downLoads[urlOrFileId] = downLoadManager;
       NSLog(@"下载相关   start 数组大小  %lu", (unsigned long)_downLoads.count);
        
        
        result(nil);
    }else if([@"stopDownload" isEqualToString:call.method]){
        NSDictionary* argsMap = call.arguments;
        NSString* urlOrFileId = argsMap[@"urlOrFileId"];
        NSLog(@"下载相关    stopDownload  %@", urlOrFileId);
        FLTTDownLoadManager* downLoadManager =   _downLoads[urlOrFileId];
        if(downLoadManager!=nil){
           [downLoadManager stopDownLoad];
        }else{
            NSLog(@"下载相关   对象为空  %lu", (unsigned long)_downLoads.count);
        }
        
        
       
        result(nil);
    }else {
        [self onMethodCall:call result:result];
    }
}

-(void) onMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result{
    
    NSDictionary* argsMap = call.arguments;
   // int64_t textureId = ((NSNumber*)argsMap[@"textureId"]).unsignedIntegerValue;
    if([NSNull null]==argsMap[@"textureId"]) {
        return;
    }
    int64_t textureId = ((NSNumber*)argsMap[@"textureId"]).unsignedIntegerValue;
//    FLTTVideoPlayer* player = _players[@(textureId)];

    if([@"play" isEqualToString:call.method]){
        [player resume];
        result(nil);
    }else if([@"pause" isEqualToString:call.method]){
        [player pause];
        result(nil);
    }else if([@"seekTo" isEqualToString:call.method]){
        NSLog(@"跳转到指定位置----------");
        [player seekTo:[[argsMap objectForKey:@"location"] intValue]];
        result(nil);
    }else if([@"setRate" isEqualToString:call.method]){ //播放速率
        NSLog(@"修改播放速率----------");
        float rate = [[argsMap objectForKey:@"rate"] floatValue];
        if (rate<0||rate>2) {
            result(nil);
            return;
        }
        [player setRate:rate];
        result(nil);
        
    }else if([@"setBitrateIndex" isEqualToString:call.method]){
        NSLog(@"修改播放清晰度----------");
        int  index = [[argsMap objectForKey:@"index"] intValue];
        [player setBitrateIndex:index];
        result(nil);
    }else if([@"dispose" isEqualToString:call.method]){
         NSLog(@"FLTTVideo  dispose   ----   ");
        [_registry unregisterTexture:textureId];
       // [_players removeObjectForKey:@(textureId)];
        //_players= nil;
        [self disposeAllPlayers];
        result(nil);
    }else{
        result(FlutterMethodNotImplemented);
    }
    
}

- (void)onPlayerSetup:(FLTTVideoPlayer*)player
         frameUpdater:(FLTTFrameUpdater*)frameUpdater
               result:(FlutterResult)result {
//    _players[@(player.textureId)] = player;
    result(@{@"textureId" : @(player.textureId)});
    
}

-(void) disposeAllPlayers{
     NSLog(@"FLTTVideo 初始化播放器状态----------");
    // Allow audio playback when the Ring/Silent switch is set to silent
    [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:nil];
    if(player){
        [player dispose];
        player = nil;
    }
}
@end
