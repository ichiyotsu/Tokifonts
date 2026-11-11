#!/usr/bin/env python3
"""Merge Tokifonts and TokiFontsResource into one catalog.json.

Tokifonts files win when the same face exists in both repositories
(matched by file size). Resource-only community fonts are appended.
Planned names with no file are kept as empty families.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "Fonts"
INDEX = Path(__file__).with_name("resource_index.json")
OUT = ROOT / "android/app/src/main/assets/catalog.json"

WEIGHTS = [
    ("extralight", 200, "ExtraLight", "特细"),
    ("ultralight", 200, "Ultralight", "极细"),
    ("semibold", 600, "SemiBold", "中粗"),
    ("demibold", 600, "DemiBold", "准粗"),
    ("extrabold", 800, "ExtraBold", "特粗"),
    ("medium", 500, "Medium", "中等"),
    ("regular", 400, "Regular", "常规"),
    ("normal", 350, "Normal", "正文"),
    ("black", 900, "Black", "极粗"),
    ("heavy", 800, "Heavy", "特粗"),
    ("bold", 700, "Bold", "粗体"),
    ("light", 300, "Light", "细体"),
    ("thin", 100, "Thin", "纤细"),
]
NUM_WEIGHT = {
    "100": (100, "Thin", "纤细"),
    "200": (200, "ExtraLight", "特细"),
    "300": (300, "Light", "细体"),
    "400": (400, "Regular", "常规"),
    "500": (500, "Medium", "中等"),
    "600": (600, "SemiBold", "中粗"),
    "700": (700, "Bold", "粗体"),
    "800": (800, "Heavy", "特粗"),
    "900": (900, "Black", "极粗"),
}
OPTICAL = {
    "extra-large": ("Extra Large", "超大"),
    "large": ("Large", "大"),
    "medium": ("Medium", "中"),
    "small": ("Small", "小"),
}
NY = re.compile(
    r"new-york-(extra-large|large|medium|small)[_-](black|bold|heavy|medium|regular|semibold)"
)

MERGE_NOTE = (
    "分类以 Tokifonts 的三层目录为准：CO 公司字库、BRD 品牌主题、CITY 城市主题。"
    "TokiFontsResource 的 Samsung、Apple、Google 分支并入公司字库；Corporate 里的微软雅黑和 Adobe 仿宋也并入公司字库，"
    "其余品牌并入品牌主题；Cities 并入城市主题。社区分支单独成类，并保留该仓库目录里的手写体和其他字体。"
    "两边都有、且文件大小一致的字体只保留一份，下载优先走 Tokifonts，失败再试 Resource 上的同一文件。"
    "Resource 标成计划更新、但没有任何文件的 Breeze Sans 和 Canva Sans 仍会列出，不能下载。"
    "Resource 说明里的「黑圆方宋体」没有单独目录，源雲明體按原仓库放在其他字体。"
)


def compact(text: str) -> str:
    return re.sub(r"[^a-z0-9]", "", text.lower())


def parse_style(filename: str, variable_hint: bool = False) -> dict:
    stem = Path(filename).stem
    stem = re.sub(r"^\d{4}\s+", "", stem)
    low = stem.lower()
    ny = NY.search(low)
    optical = None
    optical_label = None
    if ny:
        optical, optical_label = OPTICAL[ny.group(1)]
        key = ny.group(2)
        weight, weight_name, weight_label = next(
            (w, n, lab) for token, w, n, lab in WEIGHTS if token == key
        )
        italic = False
        variable = False
    else:
        flat = compact(stem)
        found = next((item for item in WEIGHTS if item[0] in flat), None)
        if found:
            _, weight, weight_name, weight_label = found
        else:
            nums = re.findall(r"(100|200|300|400|500|600|700|800|900)", flat)
            if nums:
                weight, weight_name, weight_label = NUM_WEIGHT[nums[-1]]
            elif variable_hint or "variable" in flat:
                weight, weight_name, weight_label = 400, "Variable", "可变"
            else:
                weight, weight_name, weight_label = 400, "Regular", "常规"
        italic = "italic" in flat
        variable = variable_hint or "variable" in flat
    return {
        "weight": weight,
        "weightName": weight_name,
        "weightLabel": weight_label,
        "italic": italic,
        "optical": optical,
        "opticalLabel": optical_label,
        "variable": variable,
    }


def face_from_file(path: Path, variable_hint: bool = False, inner: str | None = None) -> dict:
    name = path.name
    number = re.match(r"(\d{4})\s+", name)
    face_id = number.group(1) if number else compact(name)
    style = parse_style(name, variable_hint)
    ext = path.suffix.lower().lstrip(".")
    export = inner or name
    return {
        "id": face_id,
        "fileName": name,
        "exportName": export,
        "format": ext,
        "innerName": inner or "",
        "bytes": path.stat().st_size,
        "repo": "ichiyotsu/Tokifonts",
        "ref": "Pro",
        "path": path.relative_to(ROOT).as_posix(),
        "mirrorRepo": "",
        "mirrorRef": "",
        "mirrorPath": "",
        **style,
    }


def manual_face(**kwargs) -> dict:
    base = {
        "italic": False,
        "optical": None,
        "opticalLabel": None,
        "variable": False,
        "innerName": "",
        "mirrorRepo": "",
        "mirrorRef": "",
        "mirrorPath": "",
        "repo": "ichiyotsu/TokiFontsResource",
        "ref": "Community-contributed",
    }
    base.update(kwargs)
    return base


FAMILIES = [
    {
        "id": "pingfang-sc",
        "name": "苹方",
        "nameEn": "PingFang SC",
        "group": "co",
        "section": "apple",
        "dir": "Fonts/CO/Apple/PingFang SC",
        "summary": "苹果 2015 年在 WWDC 公布的中文系统字体，用来接替华文黑体。这里是简体 SC 的六档字重。TokiFontsResource 与 Tokifonts 的文件大小一致，只保留一份。",
        "license": "字体许可字段写明 Apple Inc internal use only. Do not distribute。版权 © 2015 Apple Inc.",
        "tags": ["简体中文"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "pingfang-hk",
        "name": "苹方-港",
        "nameEn": "PingFang HK",
        "group": "co",
        "section": "apple",
        "dir": "Fonts/CO/Apple/PingFang HK",
        "summary": "苹方的香港字形，六档字重。TokiFontsResource 注明这套文件由津岛摩卡提供。",
        "license": "文件没有许可字段。版权 © 2015 DynaComware。PingFang 是 Apple Inc. 的商标。",
        "tags": ["繁体中文", "香港"],
        "credit": "津岛摩卡",
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "sf-pro-display",
        "name": "SF Pro Display",
        "nameEn": "SF Pro Display",
        "group": "co",
        "section": "apple",
        "dir": "Fonts/CO/Apple/SF Pro Display",
        "summary": "San Francisco Pro 的大字号 Display 版本，九档字重，每档都有正体和斜体。没有简体中文。SF Pro Text 两个仓库都还没有。",
        "license": "Apple San Francisco Font 许可：仅可为运行在 Apple 系统上的软件制作界面样稿，不得把字体嵌入软件。版权 © 2015-2020 Apple Inc.",
        "alias": "San Francisco Pro Display",
        "tags": ["西文", "无简中"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "sf-pro-rounded",
        "name": "SF Pro Rounded",
        "nameEn": "SF Pro Rounded",
        "group": "co",
        "section": "apple",
        "dir": "Fonts/CO/Apple/SF Pro Rounded",
        "summary": "San Francisco 的圆角变体，九档字重，没有斜体文件。没有简体中文。",
        "license": "与 SF Pro Display 相同的 Apple San Francisco Font 许可，不得嵌入软件。",
        "tags": ["西文", "无简中"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "new-york",
        "name": "New York",
        "nameEn": "New York",
        "group": "co",
        "section": "apple",
        "dir": "Fonts/CO/Apple/NewYork Sans",
        "summary": "苹果的西文衬线家族，光学尺寸分为 Small、Medium、Large、Extra Large，各有六档字重。没有简体中文。",
        "license": "Apple New York Font 许可：仅可为 Apple 平台软件制作界面样稿，不得嵌入软件。版权 © 2017-2019 Apple Inc.",
        "tags": ["西文", "无简中", "衬线"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "google-sans",
        "name": "Google Sans",
        "nameEn": "Google Sans",
        "group": "co",
        "section": "google",
        "dir": "Fonts/CO/Google/Google Sans",
        "include": "GoogleSans",
        "exclude": "17pt",
        "summary": "从 Product Sans 发展来的谷歌西文品牌字体。这一套是 Display 字号，含四档字重、对应斜体，以及罗马和斜体两套可变字体。界面里附带了 Regular、Medium、Bold，可直接预览。",
        "license": "SIL Open Font License 1.1。版权 Copyright 2025 The Google Sans Project Authors。",
        "tags": ["西文", "无简中", "开源"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "google-sans-17",
        "name": "Google Sans 17pt",
        "nameEn": "Google Sans 17pt",
        "group": "co",
        "section": "google",
        "dir": "Fonts/CO/Google/Google Sans",
        "include": "17pt",
        "summary": "Google Sans 面向约 17pt 及以下正文的光学尺寸，四档字重和对应斜体。没有简体中文。",
        "license": "SIL Open Font License 1.1。版权 Copyright 2025 The Google Sans Project Authors。",
        "tags": ["西文", "无简中", "开源"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "source-han-sans",
        "name": "思源黑体",
        "nameEn": "Source Han Sans SC",
        "group": "co",
        "section": "google",
        "dir": "Fonts/CO/Google/Source Han Sans",
        "variable": True,
        "inner": "SourceHanSansSC-VF.ttf",
        "summary": "Adobe 与 Google 的开源泛 CJK 无衬线字体，也就是 Noto Sans CJK 的孪生版本。这里只收简体可变字体。原仓库因为体积把它打成 zip，应用下载后会解开里面的 TTF。",
        "license": "SIL Open Font License 1.1。版权 © 2014-2025 Adobe。Reserved Font Name 为 Source。",
        "alias": "Noto Sans CJK",
        "tags": ["简体中文", "可变", "开源"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "source-han-serif",
        "name": "思源宋体",
        "nameEn": "Source Han Serif CN",
        "group": "co",
        "section": "google",
        "dir": "Fonts/CO/Google/Source Han Serif",
        "summary": "思源黑体的衬线配套，也就是 Noto Serif CJK 的孪生版本。这里是简体七档字重。",
        "license": "SIL Open Font License 1.1。版权 © 2017 Adobe Systems Incorporated。Reserved Font Name 为 Source。",
        "alias": "Noto Serif CJK",
        "tags": ["简体中文", "衬线", "开源"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "one-ui-sans",
        "name": "One UI Sans",
        "nameEn": "One UI Sans",
        "group": "co",
        "section": "samsung",
        "dir": "Fonts/CO/Samsung/One UI Sans",
        "summary": "三星 Galaxy 在 One UI 6 之后使用的界面字体，文件字重从 200 到 700。TokiFontsResource 里叫 Samsung One 的文件与这些文件大小相同，按同一套字体合并。该仓库主说明曾把 One UI Sans 标成暂缺，两个仓库里其实都有文件。",
        "license": "Samsung 保留该字体软件的权利，未经授权不得使用或再分发。版权 Copyright 2015 Samsung Electronics Co., Ltd.",
        "alias": "Samsung One",
        "tags": ["西文", "无简中"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "samsung-sans",
        "name": "Samsung Sans",
        "nameEn": "Samsung Sans",
        "group": "co",
        "section": "samsung",
        "dir": "Fonts/CO/Samsung/Samsung Sans",
        "summary": "三星较早的无衬线家族，字重 Thin、Light、Regular、Medium、Bold。没有专门的简体中文。",
        "license": "文件没有许可字段。版权 © 2013 Samsung Electronics Co., Ltd.。设计方为 Dalton Maag Ltd.",
        "tags": ["西文", "无简中"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "samsung-sharp",
        "name": "Samsung Sharp Sans",
        "nameEn": "Samsung Sharp Sans",
        "group": "co",
        "section": "samsung",
        "dir": "Fonts/CO/Samsung/Samsung Sharp Sans",
        "summary": "偏标题和标识的尖锐无衬线，字重 Regular、Medium、Bold。没有专门的简体中文。",
        "license": "Sharp Sans 的定制版。Sharp Sans 版权 copyright (c) 2015 by Lucas Sharp，Sharp Sans 是 Lucas Sharp 的商标。",
        "tags": ["西文", "无简中"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "breeze-sans",
        "name": "Breeze Sans",
        "nameEn": "Breeze Sans",
        "group": "co",
        "section": "samsung",
        "summary": "Dalton Maag 为三星可穿戴设备做的另一个分支。TokiFontsResource 把它标为 Updating，Samsung 分支和 Tokifonts 都没有字体文件。",
        "license": "没有字体文件，许可未知。",
        "tags": ["计划更新", "无简中"],
        "status": "计划更新",
        "statusDetail": "TokiFontsResource 标为 Updating。两个仓库都没有可下载的字体文件。",
        "sources": ["TokiFontsResource"],
        "faces": [],
    },
    {
        "id": "yahei",
        "name": "微软雅黑",
        "nameEn": "Microsoft YaHei",
        "group": "co",
        "section": "microsoft",
        "dir": "Fonts/CO/Microsoft",
        "summary": "微软委托方正设计的简体界面黑体，这里有常规和粗体。Resource 把它放在 Corporate 分支，合并后归到公司字库。",
        "license": "按该字体所在产品的 EULA 使用，用于显示和打印。版权 © 2009 Microsoft Corporation.",
        "tags": ["简体中文"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "fangsong",
        "name": "Adobe 仿宋",
        "nameEn": "Adobe Fangsong Std",
        "group": "co",
        "section": "adobe",
        "dir": "Fonts/CO/Adobe",
        "summary": "Adobe 仿宋 Std，一档常规。Resource 把它放在 Corporate 分支，合并后归到公司字库。",
        "license": "许可页面为 adobe.com/type/legal.html。版权 © 2006, 2008, 2009 Adobe Systems Incorporated.",
        "tags": ["简体中文", "仿宋"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "misans",
        "name": "MiSans",
        "nameEn": "MiSans",
        "group": "brd",
        "section": "xiaomi",
        "dir": "Fonts/BRD/Xiaomi",
        "summary": "小米澎湃 OS 的系统字体，十档字重，从 Thin 到 Heavy。仓库说明写的是全社会免费商用，这里只有中文版本。",
        "license": "字体文件没有许可字段。版权 © 2020-2025 Beijing Xiaomi Mobile Software Co., Ltd.。Tokifonts 的品牌说明写了全社会免费商用。",
        "alias": "小米字体",
        "tags": ["简体中文"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "oppo-sans",
        "name": "OPPO Sans 4.0",
        "nameEn": "OPPO Sans",
        "group": "brd",
        "section": "oppo",
        "dir": "Fonts/BRD/OPPO",
        "variable": True,
        "summary": "OPPO ColorOS 的系统字体。这个库里是 4.0 的一个可变字体文件。",
        "license": "字体文件没有许可字段。版权 © 2019-2024 OPPO。Tokifonts 的品牌说明写了全社会免费商用。",
        "tags": ["简体中文", "可变"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "harmony-sans",
        "name": "鸿蒙黑体",
        "nameEn": "HarmonyOS Sans SC",
        "group": "brd",
        "section": "huawei",
        "dir": "Fonts/BRD/HUAWEI",
        "variable": True,
        "summary": "华为委托汉仪设计的鸿蒙系统无衬线字体。这个库里是简体可变字体，一个文件覆盖多档字重。",
        "license": "字体文件没有许可字段。版权 Copyright 2021-2024 Huawei Device Co., Ltd.。Tokifonts 的品牌说明写了面向全社会免费商用。",
        "alias": "HarmonyOS Sans",
        "tags": ["简体中文", "可变"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "douyin-sans",
        "name": "抖音美好体",
        "nameEn": "Douyin Sans",
        "group": "brd",
        "section": "douyin",
        "dir": "Fonts/BRD/Douyin",
        "summary": "抖音体验设计中心与方正字库的品牌黑体，只有 Bold 一个文件。",
        "license": "字体许可字段指向方正授权说明页。Tokifonts 的品牌说明写的是 SIL OFL 1.1。版权为北京北大方正电子有限公司。",
        "tags": ["简体中文"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "porsche-next",
        "name": "Porsche Next",
        "nameEn": "Porsche Next",
        "group": "brd",
        "section": "porsche",
        "dir": "Fonts/BRD/Porsche",
        "summary": "保时捷的西文品牌字体。这个库里只有 Regular。没有简体中文。",
        "license": "字体归 Dr. Ing. h.c. F. Porsche AG 所有，按 AS IS 提供。版权 Copyright (c) 2017-2023 Dr. Ing. h.c. F. Porsche AG.",
        "tags": ["西文", "无简中"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
    {
        "id": "canva-sans",
        "name": "Canva Sans",
        "nameEn": "Canva Sans",
        "group": "brd",
        "section": "canva",
        "summary": "TokiFontsResource 的 Corporate 名单里有 Canva Sans，并标为 Updating。任何分支都没有字体文件。",
        "license": "没有字体文件，许可未知。",
        "tags": ["计划更新"],
        "status": "计划更新",
        "statusDetail": "TokiFontsResource 主说明标为 Updating。两个仓库都没有可下载的字体文件。",
        "sources": ["TokiFontsResource"],
        "faces": [],
    },
    {
        "id": "shanghai-modeng",
        "name": "上海摩登体",
        "nameEn": "Shanghai Modeng",
        "group": "city",
        "section": "shanghai",
        "dir": "Fonts/CITY/Shanghai",
        "summary": "汉唐文化 2025 年发布的城市主题字体，一档常规。Tokifonts 的 CITY 与 Resource 的 Cities 是同一套分类。",
        "license": "文件没有许可字段。版权 © Shanghai Hantang Culture Development Co., Ltd.。城市说明写了向公众开源共享。",
        "tags": ["简体中文", "城市"],
        "sources": ["Tokifonts", "TokiFontsResource"],
    },
]


def community_faces() -> list[dict]:
    branch = "Community-contributed"
    repo = "ichiyotsu/TokiFontsResource"

    def one(face_id, path, **style):
        return manual_face(
            id=face_id,
            fileName=Path(path).name,
            exportName=style.pop("exportName", Path(path).name if not path.endswith(".7z") else "康熙字典体.ttf"),
            format=Path(path).suffix.lower().lstrip("."),
            bytes=style.pop("bytes"),
            path=path,
            repo=repo,
            ref=branch,
            **style,
        )

    # Sizes are filled after the index loads if missing; set from the known tree.
    index = json.loads(INDEX.read_text())
    sizes = {item["path"]: item["size"] for item in index[branch]}
    gen = []
    for suffix, face_id, label, weight, wname in [
        ("EL", "cc-genwan-el", "特细", 200, "ExtraLight"),
        ("L", "cc-genwan-l", "细体", 300, "Light"),
        ("R", "cc-genwan-r", "常规", 400, "Regular"),
        ("M", "cc-genwan-m", "中等", 500, "Medium"),
        ("SB", "cc-genwan-sb", "中粗", 600, "SemiBold"),
    ]:
        path = f"其他字体/GenWanMin2TC-{suffix}.otf"
        gen.append(
            one(
                face_id,
                path,
                bytes=sizes[path],
                weight=weight,
                weightName=wname,
                weightLabel=label,
            )
        )
    cotton_path = "手写体/mianhuatangpinyin.ttf"
    kangxi_path = "手写体/康熙字典体.7z"
    cubic_path = "其他字体/Cubic_11.ttf"
    return [
        {
            "id": "cotton-pinyin",
            "name": "棉花糖拼音体",
            "nameEn": "Mianhuatang Pinyin",
            "group": "community",
            "section": "handwriting",
            "summary": "社区投入的手写体，带拼音。TokiFontsResource 注明由 Gnasche. 提供。只在社区分支，Tokifonts 里没有。",
            "license": "由 Gnasche. 提供。许可以字体文件自带说明为准。",
            "credit": "Gnasche.",
            "tags": ["手写", "社区"],
            "sources": ["TokiFontsResource"],
            "faces": [
                one(
                    "cc-cotton",
                    cotton_path,
                    bytes=sizes[cotton_path],
                    weight=400,
                    weightName="Regular",
                    weightLabel="常规",
                )
            ],
        },
        {
            "id": "kangxi",
            "name": "康熙字典体",
            "nameEn": "Kangxi Dictionary",
            "group": "community",
            "section": "handwriting",
            "summary": "社区投入的手写字典体。仓库里是 7z 压缩包，里面是 康熙字典体.ttf。应用下载后会自动解压。TokiFontsResource 注明由 Gnasche. 提供。",
            "license": "由 Gnasche. 提供。许可以字体文件自带说明为准。",
            "credit": "Gnasche.",
            "tags": ["手写", "社区"],
            "sources": ["TokiFontsResource"],
            "faces": [
                one(
                    "cc-kangxi",
                    kangxi_path,
                    bytes=sizes[kangxi_path],
                    exportName="康熙字典体.ttf",
                    innerName="康熙字典体.ttf",
                    weight=400,
                    weightName="Regular",
                    weightLabel="常规",
                )
            ],
        },
        {
            "id": "genwanmin",
            "name": "源雲明體",
            "nameEn": "GenWanMin",
            "group": "community",
            "section": "other",
            "summary": "社区收录的繁体明体，五档字重：ExtraLight、Light、Regular、Medium、SemiBold。文件放在 Resource 的「其他字体」，不另建黑圆方宋体目录。",
            "license": "社区收录。源雲明體公开版本通常为 SIL OFL 1.1，仍以字体文件自带说明为准。",
            "alias": "源云明体",
            "tags": ["繁体中文", "衬线", "社区"],
            "sources": ["TokiFontsResource"],
            "faces": gen,
        },
        {
            "id": "cubic-11",
            "name": "俐方體11號",
            "nameEn": "Cubic 11",
            "group": "community",
            "section": "other",
            "summary": "像素字体，文件名 Cubic_11.ttf。Resource 把它放在「其他字体」。只在社区分支。",
            "license": "社区收录。俐方體公开版本通常为 SIL OFL，仍以字体文件自带说明为准。",
            "alias": "立方体11号",
            "tags": ["像素", "社区"],
            "sources": ["TokiFontsResource"],
            "faces": [
                one(
                    "cc-cubic",
                    cubic_path,
                    bytes=sizes[cubic_path],
                    weight=400,
                    weightName="Regular",
                    weightLabel="常规",
                )
            ],
        },
    ]


def collect_family(spec: dict) -> dict:
    if "faces" in spec:
        return {k: v for k, v in spec.items() if k not in {"dir", "include", "exclude", "variable", "inner"}}
    directory = ROOT / spec["dir"]
    files = sorted(
        p
        for p in directory.iterdir()
        if p.suffix.lower() in {".ttf", ".otf", ".zip", ".7z"}
        and (spec.get("include") is None or spec["include"] in p.name)
        and (spec.get("exclude") is None or spec["exclude"] not in p.name)
    )
    if not files:
        raise SystemExit(f"no fonts in {spec['dir']} for {spec['id']}")
    faces = []
    for path in files:
        face = face_from_file(path, spec.get("variable", False), spec.get("inner") if path.suffix.lower() == ".zip" else None)
        if spec.get("variable"):
            face["variable"] = True
            if face["weightName"] == "Regular" and "variable" not in compact(path.name) and spec["id"] in {"oppo-sans", "harmony-sans", "source-han-sans"}:
                face["weightName"] = "Variable"
                face["weightLabel"] = "可变"
        faces.append(face)
    out = {k: v for k, v in spec.items() if k not in {"dir", "include", "exclude", "variable", "inner"}}
    out["faces"] = faces
    return out


def main() -> None:
    index = json.loads(INDEX.read_text())
    families = [collect_family(spec) for spec in FAMILIES]
    families.extend(community_faces())

    by_size: dict[int, list[tuple[str, str]]] = {}
    for branch, items in index.items():
        if branch == "Community-contributed":
            continue
        for item in items:
            by_size.setdefault(item["size"], []).append((branch, item["path"]))

    used: set[tuple[str, str]] = set()
    for fam in families:
        for face in fam["faces"]:
            if face["repo"].endswith("TokiFontsResource"):
                used.add((face["ref"], face["path"]))

    errors: list[str] = []
    for fam in families:
        for face in fam["faces"]:
            if not face["repo"].endswith("Tokifonts"):
                continue
            options = by_size.get(face["bytes"], [])
            if len(options) == 1:
                branch, path = options[0]
                face["mirrorRepo"] = "ichiyotsu/TokiFontsResource"
                face["mirrorRef"] = branch
                face["mirrorPath"] = path
                used.add((branch, path))
            elif len(options) > 1:
                errors.append(f"size collision {face['bytes']} for {face['path']}: {options}")
            else:
                errors.append(f"no resource mirror for {face['path']} ({face['bytes']})")

    for branch, items in index.items():
        for item in items:
            key = (branch, item["path"])
            if key not in used:
                errors.append(f"resource file not merged: {branch}/{item['path']}")

    local = {
        p.relative_to(ROOT).as_posix()
        for p in FONTS.rglob("*")
        if p.suffix.lower() in {".ttf", ".otf", ".zip", ".7z"}
    }
    catalogued = {
        face["path"]
        for fam in families
        for face in fam["faces"]
        if face["repo"].endswith("Tokifonts")
    }
    if local != catalogued:
        errors.append(f"local only {sorted(local - catalogued)}")
        errors.append(f"catalog only {sorted(catalogued - local)}")

    ids = [face["id"] for fam in families for face in fam["faces"]]
    if len(ids) != len(set(ids)):
        errors.append("duplicate face ids")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)

    payload = {
        "app": "tokifonts",
        "version": "0.1.0",
        "mergeNote": MERGE_NOTE,
        "groups": [
            {"id": "co", "name": "公司字库", "code": "CO", "blurb": "公司与系统字体。Tokifonts 的 CO，加上 Resource 的 Samsung、Apple、Google，以及 Corporate 里的微软和 Adobe。"},
            {"id": "brd", "name": "品牌主题", "code": "BRD", "blurb": "品牌字体。Tokifonts 的 BRD，加上 Resource Corporate 里的小米、OPPO、华为、抖音、保时捷和 Canva。"},
            {"id": "city", "name": "城市主题", "code": "CITY", "blurb": "城市字体。Tokifonts 的 CITY 与 Resource 的 Cities。"},
            {"id": "community", "name": "社区贡献", "code": "COMMUNITY", "blurb": "只在 TokiFontsResource 的 Community-contributed 分支。按原目录分成手写体和其他字体。"},
        ],
        "sections": [
            {"id": "apple", "name": "苹果", "group": "co"},
            {"id": "google", "name": "谷歌", "group": "co"},
            {"id": "samsung", "name": "三星", "group": "co"},
            {"id": "microsoft", "name": "微软", "group": "co"},
            {"id": "adobe", "name": "Adobe", "group": "co"},
            {"id": "xiaomi", "name": "小米", "group": "brd"},
            {"id": "oppo", "name": "OPPO", "group": "brd"},
            {"id": "huawei", "name": "华为", "group": "brd"},
            {"id": "douyin", "name": "抖音", "group": "brd"},
            {"id": "porsche", "name": "保时捷", "group": "brd"},
            {"id": "canva", "name": "Canva", "group": "brd"},
            {"id": "shanghai", "name": "上海", "group": "city"},
            {"id": "handwriting", "name": "手写体", "group": "community"},
            {"id": "other", "name": "其他字体", "group": "community"},
        ],
        "families": families,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    faces = [face for fam in families for face in fam["faces"]]
    print(f"families {len(families)} faces {len(faces)} -> {OUT}")
    for fam in families:
        weights = ", ".join(
            f"{f['id']}:{f['weightLabel']}{'i' if f['italic'] else ''}{'v' if f['variable'] else ''}"
            for f in fam["faces"]
        ) or "(no file)"
        print(f"  {fam['id']:20} {len(fam['faces']):3}  {weights}")


if __name__ == "__main__":
    main()
