# 构建与发布

## 验证

使用完整 JDK 17+、Android Platform 37.0、Build Tools 37.0.0。

```bash
bash gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug
```

R8 固定为 9.1.57，以支持 Kotlin 2.4 元数据。不要恢复对整个应用包的 `-keep`，
Android 入口由 AGP 的规则保留。`PACKAGE_USAGE_STATS` 通过系统 Usage access/AppOps 授权，
清单只针对该声明标注 `ProtectedPermissions`，其他 Lint 检查仍正常执行。

## 签名

Release 不再使用 Android 通用 Debug 签名。设置 `USAGE_SIGNING_PROPERTIES` 指向仓库外的私密属性文件：

```properties
storeFile=/absolute/private/path/UsageTimeline.p12
storePassword=<keystore password>
keyAlias=usagetimeline
keyPassword=<key password>
```

属性文件与私钥仅供构建使用，不能提交 Git 或上传 Release。请离线备份同一把发布密钥，
后续升级包必须使用相同签名。丢失密钥后无法覆盖安装旧版本。
本次云环境生成的密钥与配置位于 `/workspace/usage-signing/`，尚未备份至外部。

```bash
export USAGE_SIGNING_PROPERTIES=/absolute/private/path/signing.properties
bash gradlew --no-daemon --max-workers=4 :app:assembleRelease
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

未设置签名配置时只会生成 unsigned APK，不可作为可安装的发布产物上传。
首次从原先自行编译的 Debug 签名版本切换到 Release 签名，需要先卸载旧包；
会重置应用设置和使用情况访问授权，但不会清除系统保留的使用事件。

## 发布检查

- 更新 `versionCode`、`versionName`，执行测试、Lint、Release 构建和 APK 签名校验。
- 在 Android 设备上验证授权返回、快速切日、下拉刷新、七天详情、主题与后台恢复。
- 尤其在记录较多的设备上分别测首次读取和连续刷新的耗时。JVM 事件解析测试不等同于端到端测试。
- 将签名 APK、SHA-256 校验文件及变更说明附到对应 Git tag 的 GitHub Release。

本次云环境没有可用 Android 设备或 KVM，设备验证尚未执行；发布说明中应保留此限制。
