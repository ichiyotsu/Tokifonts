# Tokifonts

Tokifonts 把字体文件收在 `Fonts/`，第一层目录是 `CO`、`BRD`、`CITY`。`Fonts/BRD/Readme.md` 的标题是 Brand-themed 品牌主题，`Fonts/CITY/Shanghai/Readme.md` 的标题是 City-themed 城市主题。文件主要是 `.ttf` 和 `.otf`，思源黑体可变字体是一个 zip。下载后用系统的字体安装功能安装，仓库里没有安装脚本。

| 路径 | 字体 | 收录 | 字体版本 |
| --- | --- | --- | --- |
| `Fonts/CO/Samsung/One UI Sans` | SamsungOneUI | 6 个 `.ttf`：200、300、400、500、600、700 | 1.000 |
| `Fonts/CO/Samsung/Samsung Sans` | Samsung Sans | 5 个 `.ttf`：Bold、Light、Medium、Regular、Thin | 1.102 |
| `Fonts/CO/Samsung/Samsung Sharp Sans` | Samsung Sharp Sans | 3 个 `.otf`：Bold、Medium、Regular | 1.000 |
| `Fonts/CO/Apple/PingFang SC` | 苹方 | 6 个 `.ttf`：Bold、ExtraLight、Heavy、Light、Medium、Regular | 10.11d9e1 |
| `Fonts/CO/Apple/PingFang HK` | 苹方-港 | 6 个 `.ttf`：Light、Medium、Regular、Semibold、Thin、Ultralight | 4.100 |
| `Fonts/CO/Apple/SF Pro Display` | SF Pro Display | 18 个 `.otf`。字重 Black、Bold、Heavy、Light、Medium、Regular、Semibold、Thin、Ultralight，各有正体和斜体 | 16.0d18e1 |
| `Fonts/CO/Apple/SF Pro Rounded` | SF Pro Rounded | 9 个 `.otf`：Black、Bold、Heavy、Light、Medium、Regular、Semibold、Thin、Ultralight | 16.0d18e1 |
| `Fonts/CO/Apple/NewYork Sans` | New York Small、Medium、Large、Extra Large | 24 个 `.ttf`。光学尺寸 Small、Medium、Large、Extra Large，字重 Black、Bold、Heavy、Medium、Regular、Semibold | 15.0d5e2m |
| `Fonts/CO/Google/Google Sans` | Google Sans | 10 个 `.ttf`：Regular、Medium、SemiBold、Bold 及对应斜体。`3009`、`3010` 是可变字体 | 13.002 |
| `Fonts/CO/Google/Google Sans` | Google Sans 17pt | 8 个 `.ttf`：Regular、Medium、SemiBold、Bold 及对应斜体 | 13.002 |
| `Fonts/CO/Google/Source Han Sans` | Source Han Sans SC VF | 1 个 zip，内有可变字体 `SourceHanSansSC-VF.ttf` | 2.005 |
| `Fonts/CO/Google/Source Han Serif` | 思源宋体 CN | 7 个 `.ttf`：ExtraLight、Light、Regular、Medium、SemiBold、Bold、Heavy | 1.00 |
| `Fonts/CO/Microsoft` | 微软雅黑 | 2 个 `.ttf`：`4001 Microsoft Yahei.ttf`、`4002 Microsoft Yahei Bold.ttf` | 6.02 |
| `Fonts/CO/Adobe` | Adobe 仿宋 Std | 1 个 `.otf`，样式 Regular | 5.010 |
| `Fonts/BRD/Xiaomi` | MiSans | 10 个 `.ttf`：Thin、ExtraLight、Light、Normal、Regular、Medium、Demibold、Semibold、Bold、Heavy | 4.009 |
| `Fonts/BRD/OPPO` | OPPO Sans 4.0 | 1 个可变 `.ttf`。name 表中的字重：Light、Regular、Medium、SemiBold、Bold，另有 Italic | 1.700 |
| `Fonts/BRD/HUAWEI` | 鸿蒙黑体 | 1 个可变 `.ttf`。name 表中的字重：Thin、UltraLight、Light、Regular、Medium、SemiBold、Bold、Heavy、Black，另有 Italic | 2.040 |
| `Fonts/BRD/Douyin` | 抖音美好体 | 1 个 `.otf`：Bold | 1.00 |
| `Fonts/BRD/Porsche` | Porsche Next | 1 个 `.ttf`，样式 Regular | 2.002 |
| `Fonts/CITY/Shanghai` | 上海摩登体 | 1 个 `.otf`，样式常规 | 1.000 |

上表的版本来自字体文件 name 表里的 version 字符串，是字体自己的版本。苹方-港的完整字符串是 `Version 4.100;July 27, 2024;FontCreator 15.0.0.2963 64-bit`。思源宋体 CN 的完整字符串是 `Version 1.00 April 7, 2017, initial release`。Google Sans 的完整字符串是 `Version 13.002;[5e3df34c1]`。

`Fonts/` 里共有 87 个 `.ttf`、33 个 `.otf`、1 个 `.zip`。

## 已发布版本

仓库还没有 tag，也没有 GitHub Release。发布页是 [github.com/ichiyotsu/Tokifonts/releases](https://github.com/ichiyotsu/Tokifonts/releases)。

## 版本规则

`X.Y.Z`（如 `0.1.0`）是正式版。`X.Y.Z-bN`（如 `0.1.1-b1`）是测试版。

发布顺序是 `0.1.0` → `0.1.1-b1` → `0.1.1-b2` → … → `0.1.1`。测试版比已经发布的正式版超前几个号，是正常情况。

tag 使用 `v` 前缀，例如 `v0.1.0`、`v0.1.1-b1`。带 `-b` 的 tag 发布为 prerelease。

仓库里没有 `.github/workflows`。

## 获取和使用

仓库地址是 [github.com/ichiyotsu/Tokifonts](https://github.com/ichiyotsu/Tokifonts)。默认分支是 `Pro`。

```bash
git clone --depth 1 --branch Pro https://github.com/ichiyotsu/Tokifonts.git
```

2026-11-11 的 `Pro`：`.git` 压缩包约 331 MB，检出的 `Fonts/` 约 529 MB。

也可以在网页上按文件下载。`Fonts/CO/Google/Source Han Sans/3019 SourceHanSansSC-VF.zip` 是 20,313,312 字节，解开后是 `SourceHanSansSC-VF.ttf`（36,035,552 字节，Version 2.005，可变字体）。同目录的 `Readme.md` 写明 Windows 11 文件管理器可以解压这个包。

`.ttf` 和 `.otf` 用系统的字体安装功能安装。仓库里没有安装脚本。

可变字体文件是 `Fonts/CO/Google/Google Sans/3009 GoogleSansVariable.ttf`、`Fonts/CO/Google/Google Sans/3010 GoogleSansItalicVariable.ttf`、`Fonts/BRD/OPPO/4013 OPPO Sans 4.0.ttf`、`Fonts/BRD/HUAWEI/4014 HarmonyOS_Sans_SC.ttf`，以及 zip 里的 `SourceHanSansSC-VF.ttf`。

## 目录结构

```
Fonts/
├── CO/
│   ├── Adobe/
│   ├── Apple/
│   │   ├── NewYork Sans/
│   │   ├── PingFang HK/
│   │   ├── PingFang SC/
│   │   ├── SF Pro Display/
│   │   └── SF Pro Rounded/
│   ├── Google/
│   │   ├── Google Sans/
│   │   ├── Source Han Sans/
│   │   └── Source Han Serif/
│   ├── Microsoft/
│   └── Samsung/
│       ├── One UI Sans/
│       ├── Samsung Sans/
│       └── Samsung Sharp Sans/
├── BRD/
│   ├── Douyin/
│   ├── HUAWEI/
│   ├── OPPO/
│   ├── Porsche/
│   └── Xiaomi/
└── CITY/
    └── Shanghai/
```

`CO` 下是 Adobe、Apple、Google、Microsoft、Samsung。字体文件名以四位编号开头，例如 `1001`、`2001`、`3001`、`4001`、`5001`。

有正文的说明：

- `Fonts/BRD/Readme.md`
- `Fonts/CITY/Shanghai/Readme.md`
- `Fonts/CO/Adobe/Readme.md`
- `Fonts/CO/Apple/Readme.md`
- `Fonts/CO/Google/Readme.md`
- `Fonts/CO/Google/Source Han Sans/Readme.md`
- `Fonts/CO/Microsoft/Readme.md`

`Fonts/readme.md` 和其余子目录里的 `Readme.md` 是空文件。英文入口是 `README-EN.md`，正文在本文件。

## 许可

仓库根目录没有许可证文件。每套字体使用该字体文件自带的许可。下表只记录字体 name 表里的版权、许可字段和许可网址。子目录 `Readme.md` 另有写法的，写在同一行。

| 字体 | 自带许可 |
| --- | --- |
| Google Sans、Google Sans 17pt | SIL Open Font License, Version 1.1。许可网址 [openfontlicense.org](https://openfontlicense.org)。版权为 Copyright 2025 The Google Sans Project Authors (github.com/googlefonts/googlesans)。 |
| Source Han Sans SC VF | SIL Open Font License, Version 1.1。许可网址 [scripts.sil.org/OFL](http://scripts.sil.org/OFL)。版权为 © 2014-2025 Adobe，Reserved Font Name 为 Source。 |
| 思源宋体 CN | SIL Open Font License, Version 1.1。许可网址 [scripts.sil.org/OFL](http://scripts.sil.org/OFL)。版权为 Copyright © 2017 Adobe Systems Incorporated，Reserved Font Name 为 Source。 |
| 微软雅黑 | 许可字段写明：按该字体所在产品的 EULA 使用，用于显示和打印；只能按字体里的嵌入限制嵌入内容，以及临时下载到打印机或其他输出设备来打印。许可网址 [microsoft.com/typography/fonts](http://www.microsoft.com/typography/fonts)。版权为 © 2009 Microsoft Corporation。 |
| SF Pro Display、SF Pro Rounded | 许可字段为 Apple San Francisco Font 的许可协议。`2023 SF-Pro-Display-Regular.otf` 写明仅可用于为运行在 Apple iOS、iPadOS、macOS 或 tvOS 上的软件制作界面样稿，使用方为 Registered Apple Developer 或经 Apple 书面允许的人，并写明不得把字体嵌入软件程序。字段后半是符号使用限制。版权为 © 2015-2020 Apple Inc.。 |
| New York Small、Medium、Large、Extra Large | 许可字段为 Apple New York Font 的许可协议。`2056 new-york-medium_regular.ttf` 写明仅可用于为运行在 Apple 操作系统上的软件制作界面样稿，使用方为注册 Apple Developer 或经 Apple 书面允许的人，并写明不得把字体嵌入软件程序。版权为 © 2017-2019 Apple Inc.。 |
| 苹方 | 许可字段为 `Apple Inc internal use only. Do not distribute.` 版权为 Copyright © 2015 Apple Inc.。 |
| 苹方-港 | 文件没有许可字段。版权为 Copyright © 2015 DynaComware。商标字段写明 PingFang is a trademark of Apple Inc.。 |
| SamsungOneUI | 许可字段为 `All rights of this font software are reserved by Samsung Electronics Co.,LTD. Any unauthorized use or distribution is expressly prohibited.` 版权为 Copyright 2015 Samsung Electronics Co., Ltd.。 |
| Samsung Sans | 文件没有许可字段。版权为 Copyright © 2013 Samsung Electronics Co., Ltd.。设计方字段为 Dalton Maag Ltd。 |
| Samsung Sharp Sans | 文件没有单独的许可字段。版权字段写明它是 Sharp Sans 的定制版，Sharp Sans 的版权为 copyright (c) 2015 by Lucas Sharp。商标字段写明 Sharp Sans is a trademark of Lucas Sharp。 |
| Porsche Next | 许可字段写明字体归 Dr. Ing. h.c. F. Porsche AG 所有，字体软件按 “AS IS” 提供，并写明联系 Dr. Ing. h.c. F. Porsche AG 的 Production and Marketing 获取使用许可和限制。许可网址是 [保时捷联系页](http://www.porsche.com/germany/dialog/n90/contactandinformations-locations/locations-porscheag/locationsporscheag-stuttgart/)。版权为 Copyright (c) 2017-2023 Dr. Ing. h.c. F. Porsche AG.。 |
| 抖音美好体 | 许可字段指向 [foundertype.com 的授权说明页](http://www.foundertype.com/index.php/About/bookAuth/key/my_grfs.htm)。版权为北京北大方正电子有限公司。`Fonts/BRD/Readme.md` 写的是 SIL Open Font License 1.1。 |
| MiSans | 文件没有许可字段。版权为 Copyright © 2020-2025 Beijing Xiaomi Mobile Software Co.,Ltd.。厂商网址 [hanyi.com.cn](https://www.hanyi.com.cn/)。`Fonts/BRD/Readme.md` 写了全社会免费商用。 |
| OPPO Sans 4.0 | 文件没有许可字段。版权为 copyright © 2019-2024 by OPPO。`Fonts/BRD/Readme.md` 写了全社会免费商用。 |
| 鸿蒙黑体 | 文件没有许可字段。版权为 Copyright 2021-2024 Huawei Device Co., Ltd.。厂商字段为 Huawei Device Co., Ltd & Hanyi Fonts。`Fonts/BRD/Readme.md` 写了面向全社会免费商用。 |
| Adobe 仿宋 Std | 文件没有许可正文。许可网址 [adobe.com/type/legal.html](http://www.adobe.com/type/legal.html)。版权为 Copyright © 2006, 2008, 2009 Adobe Systems Incorporated。 |
| 上海摩登体 | 文件没有许可字段。版权为 Copyright © Shanghai Hantang Culture Development Co., Ltd.。厂商网址 [mallikatype.com](https://mallikatype.com)。`Fonts/CITY/Shanghai/Readme.md` 写了向公众开源共享。 |
