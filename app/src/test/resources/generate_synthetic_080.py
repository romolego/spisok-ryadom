"""Build deterministic, entirely invented ZIP8 fixtures used by Refine080DataTest.

No input backup, user files, network connection, or third-party packages are used.
The paired ZIP fixtures contain only numbered, fictional test entities.
Run: python app/src/test/resources/generate_synthetic_080.py
"""

import copy
import json
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parent
EPOCH = 946684800000  # 2000-01-01T00:00:00Z: synthetic, fixed test clock.
TYPE_NAMES = [
    "Молочка", "Выпечка", "Консервы", "Орехи", "Овощи и фрукты", "Мясо",
    "Крупы", "Бытовая химия", "Гигиена", "Готовая еда",
    "Гастроном (готовая еда)", "Заморозка",
]
EXTRA_TYPES = [
    ("Рыба и морепродукты", "fish"), ("Чай и кофе", "tea"),
    ("Вода", "water"), ("Соки и газировка", "bottle"),
    ("Соусы и специи", "herbs"), ("Снеки", "snacks"),
    ("Гигиена и косметика", "hygiene"),
    ("Корма и товары для животных", "pets"),
    ("Лекарства и медизделия", "medicine"),
]
UNIT_NAMES = ["шт", "упаковка", "г", "кг", "л", "мл", "банка", "бутылка", "десяток"]


def department_type(identifier, name, icon=""):
    return dict(id=identifier, name=name, normalizedName=name.lower(),
                displayOrder=identifier - 1, accent="", iconKey=icon)


def snapshot():
    types = [department_type(i, name) for i, name in enumerate(TYPE_NAMES, 1)]
    shops = [dict(id=i, name=f"Тестовый магазин {i:02d}",
                  address=f"Тестовая улица, {i}", note=f"Синтетический магазин {i}",
                  displayOrder=i - 1, showUrgentInCollapsed=None, boostUrgentShop=None,
                  manualOrderOverride=False, cityId=1, networkId=None)
             for i in range(1, 15)]
    # Forty-seven valid, retained placements. Shops 11/12 intentionally have none.
    placements = []
    for shop in [*range(1, 11), 13, 14]:
        for kind in range(1, 5):
            if shop == 7 and kind == 4:
                continue
            if shop == 8 and kind == 4:
                kind = 12  # A legitimate, used frozen-food department is retained.
            placements.append(dict(id=len(placements) + 1, shopId=shop, typeId=kind,
                                   name=TYPE_NAMES[kind - 1], displayOrder=kind - 1))
    retained = copy.deepcopy(placements)
    # Thirteen unused placements model a past broadcast error, all removable.
    broadcast_pairs = [(shop, kind) for shop in [11, 12, 13, 14, 7, 6]
                       for kind in [11, 12]] + [(8, 11)]
    for shop, kind in broadcast_pairs:
        placements.append(dict(id=len(placements) + 1, shopId=shop, typeId=kind,
                               name=TYPE_NAMES[kind - 1], displayOrder=kind - 1))

    branches = [dict(id=i, shopId=1 if i <= 3 else i - 2,
                     address=f"Синтетическая точка {i:02d}", displayOrder=(i - 1) % 3,
                     note=f"Описание тестовой точки {i}", venueId=None)
                for i in range(1, 10)]
    products = [dict(id=i, name=f"Тестовый товар {i:03d}", productGroupId=(i - 1) % 8 + 1,
                     recipientId=None if i % 3 == 0 else (i - 1) % 2 + 1,
                     defaultUnit=UNIT_NAMES[(i - 1) % len(UNIT_NAMES)],
                     defaultQuantity=float((i - 1) % 4 + 1),
                     note=f"Синтетическая заметка {i}", purchaseType="offline",
                     sellerUrl=None, productUrl=None, priceValue=None, priceDate=None,
                     isTemporary=False, stableId=f"synthetic-080-product-{i:03d}",
                     preferCityShopOrder=False, isFavorite=i % 5 == 0, accent="")
                for i in range(1, 121)]
    links, availability = [], []
    for product in products:
        i = product["id"]
        shop = 1 if i <= 12 else (i - 1) % 14 + 1
        options = [p for p in retained if p["shopId"] == shop]
        department = options[(i - 1) % len(options)]["id"] if options else None
        links.append(dict(id=len(links) + 1, productId=i, shopId=shop,
                          restrictBranches=i <= 12, priority=1, departmentId=department))
        if i <= 12:
            availability.append(dict(productId=i, branchId=(i - 1) % 3 + 1, shopId=1))
    # A second retailer for fourteen products exercises many-to-many preservation.
    for i in range(1, 15):
        shop = i % 13 + 2
        options = [p for p in retained if p["shopId"] == shop]
        links.append(dict(id=len(links) + 1, productId=i, shopId=shop,
                          restrictBranches=False, priority=2,
                          departmentId=options[0]["id"] if options else None))
    entries = []
    for i in range(1, 22):
        link = links[i - 1]
        point = (i - 1) % 3 + 1 if i <= 12 else None
        entries.append(dict(id=i, productId=i, quantity=float(i % 4 + 1),
                            unit=products[i - 1]["defaultUnit"],
                            assignedBranchId=point, assignedShopId=link["shopId"],
                            assignedDepartmentId=link["departmentId"],
                            isBought=i % 6 == 0, isUrgent=i % 7 == 0,
                            note=f"Тестовая покупка {i}", comment=f"Комментарий {i}",
                            createdAt=EPOCH + i * 1000, updatedAt=EPOCH + i * 1000,
                            awaitingShopCityId=None, explicitNoShop=False))
    data = dict(branchDepartmentOrder=[], productBranchDepartments=[], retailNetworks=[],
                venues=[], departmentTypes=types, shopBranches=branches,
                productBranchAvailability=availability, products=products,
                productGroups=[dict(id=i, name=f"Тестовая группа {i}", displayOrder=i - 1,
                                    accent="", iconKey="") for i in range(1, 9)],
                recipients=[dict(id=i, name=f"Тестовый получатель {i}", displayOrder=i - 1,
                                 accent="", iconKey="") for i in [1, 2]],
                shops=shops, shopDepartments=placements, productShopLinks=links,
                shoppingListEntries=entries,
                units=[dict(id=i, name=name, displayOrder=i - 1)
                       for i, name in enumerate(UNIT_NAMES, 1)],
                cities=[dict(id=1, name="Тестовый город")])
    before = dict(version=8, kind="full", exportedAt="2000-01-01T00:00:00",
                  preferences={}, data=data)
    after = copy.deepcopy(before)
    revised = after["data"]
    revised["shopDepartments"] = retained
    revised["departmentTypes"][2]["iconKey"] = "canned"
    revised["departmentTypes"][3]["iconKey"] = "nuts"
    revised["departmentTypes"] += [department_type(i, name, icon)
                                   for i, (name, icon) in enumerate(EXTRA_TYPES, 13)]
    revised["units"] += [dict(id=i, name=name, displayOrder=i - 1)
                         for i, name in enumerate(["пачка", "пакет", "рулон"], 10)]
    assert len(data["products"]) == 120 and len(data["shoppingListEntries"]) == 21
    assert len(links) == 134 and len(branches) == 9 and len(availability) == 12
    assert len(placements) == 60 and len(retained) == 47
    return before, after


def write_zip(path, value):
    # Fixed metadata and canonical UTF-8 encoding make regeneration byte-for-byte stable.
    info = zipfile.ZipInfo("manifest.json", date_time=(2000, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o600 << 16
    content = json.dumps(value, ensure_ascii=False, sort_keys=True,
                         separators=(",", ":")).encode("utf-8")
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(info, content)


if __name__ == "__main__":
    for name, value in zip(["synthetic-backup-before-080.zip", "synthetic-backup-after-080.zip"],
                           snapshot()):
        write_zip(ROOT / name, value)
        print(f"Generated synthetic fixture: {name}")
