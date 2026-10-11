#!/usr/bin/env python3
"""Generate the checked-in Xcode project without XcodeGen or third-party dependencies."""
from pathlib import Path
import hashlib, json
root = Path(__file__).resolve().parents[1]
objects = {}
def uid(s): return hashlib.sha1(s.encode()).hexdigest()[:24].upper()
def add(name, value):
    i=uid(name); objects[i]=value; return i
def q(value): return json.dumps(str(value), ensure_ascii=False)
sources = [str(p.relative_to(root)) for p in sorted((root/'FujiGallery').glob('*.swift'))] + ['Core/Photo.swift', 'Native/FujiBridge.c']
sources += ['../lib/'+f+'.c' for f in ['fuji','data','discovery','exif','fuji_usb','object','plat']]
sources += ['../lib/fp/src/'+f+'.c' for f in ['d185','data','xml','ezxml']]
sources += ['../lib/libpict/src/'+f+'.c' for f in ['operations','packet','enums','data','enum_dump','lib','canon','liveview','ml','conv','generic','transport','ip','bind','canon_adv','stuff','no_usb']]
refs=[]; builds=[]
for path in sources:
    ref=add('ref:'+path, '{isa = PBXFileReference; lastKnownFileType = '+('sourcecode.swift' if path.endswith('.swift') else 'sourcecode.c')+'; path = '+q(path)+'; sourceTree = SOURCE_ROOT; }')
    refs.append(ref); builds.append(add('build:'+path,'{isa = PBXBuildFile; fileRef = '+ref+'; }'))
asset=add('assets','{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = FujiGallery/Assets.xcassets; sourceTree = SOURCE_ROOT; }')
assetBuild=add('assetsBuild','{isa = PBXBuildFile; fileRef = '+asset+'; }');refs.append(asset)
product=add('product','{isa = PBXFileReference; explicitFileType = wrapper.application; path = FujiGallery.app; sourceTree = BUILT_PRODUCTS_DIR; }')
group=add('group','{isa = PBXGroup; children = ('+','.join(refs+[product])+'); sourceTree = "<group>"; }')
sourcePhase=add('sources','{isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ('+','.join(builds)+'); runOnlyForDeploymentPostprocessing = 0; }')
resourcePhase=add('resources','{isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ('+assetBuild+'); runOnlyForDeploymentPostprocessing = 0; }')
frameworkPhase=add('frameworks','{isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; }')
settings={'SDKROOT':'iphoneos','IPHONEOS_DEPLOYMENT_TARGET':'16.0','SWIFT_VERSION':'5.0','CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','GCC_C_LANGUAGE_STANDARD':'gnu11','HEADER_SEARCH_PATHS':'"$(SRCROOT)/../lib" "$(SRCROOT)/../lib/libpict/src" "$(SRCROOT)/../lib/fp/src"','PRODUCT_BUNDLE_IDENTIFIER':'com.xiaoxiaotao.fujigallery','PRODUCT_NAME':'$(TARGET_NAME)','INFOPLIST_FILE':'FujiGallery/Info.plist','SWIFT_OBJC_BRIDGING_HEADER':'FujiGallery/BridgingHeader.h','CODE_SIGN_ENTITLEMENTS':'FujiGallery/FujiGallery.entitlements','CODE_SIGN_STYLE':'Automatic','TARGETED_DEVICE_FAMILY':'1','MARKETING_VERSION':'0.1.0','CURRENT_PROJECT_VERSION':'1','ASSETCATALOG_COMPILER_APPICON_NAME':'AppIcon','ENABLE_USER_SCRIPT_SANDBOXING':'YES','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','SUPPORTS_MACCATALYST':'NO'}
configs=[]
for name in ['Debug','Release']:
    s=dict(settings);s['SWIFT_OPTIMIZATION_LEVEL']='-Onone' if name=='Debug' else '-O';s['GCC_PREPROCESSOR_DEFINITIONS']='DEBUG=1 $(inherited)' if name=='Debug' else 'NDEBUG=1 $(inherited)'
    configs.append(add('config:'+name,'{isa = XCBuildConfiguration; buildSettings = {'+' '.join(k+' = '+q(v)+';' for k,v in s.items())+'}; name = '+name+'; }'))
configList=add('configList','{isa = XCConfigurationList; buildConfigurations = ('+','.join(configs)+'); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }')
target=add('target','{isa = PBXNativeTarget; buildConfigurationList = '+configList+'; buildPhases = ('+','.join([sourcePhase,frameworkPhase,resourcePhase])+'); buildRules = (); dependencies = (); name = FujiGallery; productName = FujiGallery; productReference = '+product+'; productType = "com.apple.product-type.application"; }')
project=add('project','{isa = PBXProject; attributes = {LastUpgradeCheck = 1600; TargetAttributes = {'+target+' = {CreatedOnToolsVersion = 16.0; SystemCapabilities = {com.apple.HotspotConfiguration = {enabled = 1;};};};};}; buildConfigurationList = '+configList+'; compatibilityVersion = "Xcode 14.0"; developmentRegion = zh-Hans; knownRegions = ("zh-Hans",en,Base); mainGroup = '+group+'; projectDirPath = ""; projectRoot = ""; targets = ('+target+'); }')
out=root/'FujiGallery.xcodeproj';out.mkdir(exist_ok=True)
(out/'project.pbxproj').write_text('// !$*UTF8*$!\n{archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+'\n'.join(k+' = '+v+';' for k,v in objects.items())+'\n}; rootObject = '+project+'; }\n')
scheme=out/'xcshareddata/xcschemes';scheme.mkdir(parents=True,exist_ok=True)
(scheme/'FujiGallery.xcscheme').write_text('<?xml version="1.0" encoding="UTF-8"?><Scheme LastUpgradeVersion="1600" version="1.3"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="'+target+'" BuildableName="FujiGallery.app" BlueprintName="FujiGallery" ReferencedContainer="container:FujiGallery.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="'+target+'" BuildableName="FujiGallery.app" BlueprintName="FujiGallery" ReferencedContainer="container:FujiGallery.xcodeproj"/></BuildableProductRunnable></LaunchAction><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>')
print(out)
