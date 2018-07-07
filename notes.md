## 2018.7.8 Magisk v16.6
(XDA Post: [here](https://forum.xda-developers.com/showpost.php?p=77014053&postcount=43))

Hello, long time no see guys! Wondering why v16.5 is skipped? Because we went through so many internal testing that we ran out of version numbers :p

### Full Treble-ish
Magisk relies on files placed in `/data` to work properly, so if you performed factory reset, Magisk couldn't fully function until you re-install. In this new version, a stub Magisk Manager APK is embedded into `magiskinit`, and it will be installed if no manager is detected. The stub will download and install the full Magisk Manager, then the full version will reconstruct a proper Magisk environment. This is particular useful for people switching to/across GSI ROMs: you can swap system images with full wipes without the need to reinstall Magisk! This also means that people can share pre-rooted boot images with others using the same device, as Magisk is fully functional with solely a patched boot image.

### No More Mysterious Root Loss
Several users has been constantly reporting the "root loss" issue: Magisk will randomly stop working. Actually, over a year ago when MagiskHide was just announced, a similar issue was widespread and fixed at that time. The cause of the issue has something to do with the fact that Magisk unexpectedly unmounts stuffs in Zygote's mount namespace. Recently I found out that there are devices running **multiple** Zygote servers at a time! This weird edge case was not handled by MagiskHide, and thus root loss issues still occurs on some users. A new mechanism is implemented to mitigate this notorious bug.

However, there were still users losing root caused by a more serious issue: magisk daemon crashing. To cure the symptoms but not the disease, I introduced **Invincible Mode** a while ago, but the reason of the crashes was never identified. I spent some time rewriting the most complicated (and suspicious) part: logcat monitoring, introducing a new daemon, `magisklogd`, which will work hand in hand with the main daemon, `magiskd`. Internal testers who used to experience daemon crashes no longer have any issues up to this point; additionally a new invincible mode implemented by constant handshaking between the 2 daemons still remains, should the daemon crashes.

### Miscellaneous 
Tons of other bug fixes, optimizations, new support is also added to this release, here I'll briefly go through them.

- Magisk Manager will preserve the random package name when upgrading within the app. Magisk will no longer prefer the package name `com.topjohnwu.magisk` over repackaged (hidden) Magisk Manager to prevent malware from targeting this specific package name. If you have a repackaged Magisk Manager installed, `com.topjohnwu.magisk` will be forcefully denied root access. You can use **Restore Magisk Manager** in settings, or uninstall the repackaged Magisk Manager to unlock `com.topjohnwu.magisk`.
- The logic to calculate free space in ext4 images is replaced with new extremely precise methods, hopefully no more module installation failures caused by images should happen. All modules using template `1500` will automatically benefit from the new free space calculation method on Magisk v16.6+, no additional changes are needed.
- Support for Samsung Galaxy S9/S9+ is officially added.
- Magisk v16.4 switched to 32-bit only binaries and caused issues in some apps. A new wrapper script is added to eliminate all possible quirks.
- Treble GSIs (e.g. phh AOSP) sometimes require replacing `adbd` in ramdisk and used to conflict with Magisk. It is now fixed and ADB will fully function when using GSIs.
- LineageOS introduced addon.d-v2 to A/B partition devices, the addon.d script is updated to be A/B aware (untested on my side)

### Note to ROM Developers
If you are embedding Magisk Zip into your ROM, and your ROM uses Aroma Installer, please be aware that on some devices (most likely Samsung devices), it is possible that the installation process of Magisk could break Aroma. Please test flashing your ROM zip before releasing to your users. If you found out you are the few unfortunate devices, unzip the Magisk zip, and do the following changes and re-zip the installer:

```
# Remove or comment out these 2 lines in META-INF/com/google/android/updater-script

eval $BOOTSIGNER -verify < $BOOTIMAGE && BOOTSIGNED=true
$BOOTSIGNED && ui_print "- Boot image is signed with AVB 1.0"
```

The reason why Aroma Installer breaks is unknown. Maybe consider letting the project that's abandoned for nearly 5 years go?

### Full Changelog: [here](https://forum.xda-developers.com/showpost.php?p=68966755&postcount=2)

