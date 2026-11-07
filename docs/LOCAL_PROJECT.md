# Локальный проект

Рабочая папка —`D:\projects\spisok-ryadom`. Это корень Git-проекта, исходников Android и общей базы знаний. Основная среда работы —Codex Desktop; Android Studio используется для Android-инструментов. Отдельной конкурирующей памяти у другого агента нет.

Перед изменениями открыть [маршрутизатор](../_agent_knowledge/11_KNOWLEDGE_ROUTER.md), [правила](../_agent_knowledge/03_AGENT_RULES.md), [текущее состояние](../_agent_knowledge/01_CURRENT_STATE.md), [атлас](../_agent_knowledge/41_ARCHITECTURE_ATLAS.md) и [сопровождение](../_agent_knowledge/42_MAINTENANCE_GUIDE.md).

Текущая итерация0.31.1(44), Room28/ZIP26 —[аудит63](../_agent_knowledge/63_INTEGRITY_AUDIT_311.md). Проверены последние пакеты предзаполнения/массового ввода и виджета вместе с остальными сценариями приложения. Пакет `com.spisokryadom.app.next`, подпись, схема и формат копий прежние. Скрытый русский справочник содержит329 товаров и451 имя/алиас; стандартные библиотеки30 товарных групп и30 отделов сохраняются. Личные базы, фото, ключи и промежуточные проверки находятся вне публичных исходников.

Полный набор с адресным повтором и сборка завершены: 1661/1661 уникальных успешных проверок, lint0 ошибок. [Проверка](releases/0.31.1/README.md). Публикация завершена: [PR](https://github.com/romolego/spisok-ryadom/pull/8) слит обычным merge вmain, [v0.31.1](https://github.com/romolego/spisok-ryadom/releases/tag/v0.31.1) опубликован. Все7 файлов повторно скачаны без авторизации, размеры/SHA256 и CRC ZIP совпали. Source commit `b907e27ea6ba043b620a2d716ae508639b86043a`, merge/tag `52f497b2d281c1d64954a0ca7e6fcb997ff3b8f1`. Публичный source ZIP соответствует390 проверенным файлам. Квитанция —`docs/publications/0.31.1.json`. Путь нового APK —`.local/artifacts/0.31.1/SpisokPokupok-0.31.1.apk`. Предыдущая публикация0.29.2 сохранена в своей исторической квитанции.

Рабочая ветка —`main`; проверенная ветка `codex/integrity-audit-0.31.1` сохранена. Исходный чистый коммит `a11b158` сохранён в ветке `codex/widget-privacy-motion-0.31.0`; исходный архив и изолированный checkout —`.local/audit311`. Откат кода при чистом рабочем дереве: `git switch codex/widget-privacy-motion-0.31.0`. Эта команда переключает исходники, а не версию установленного приложения и его базу.

В проекте используются Gradle Wrapper8.13, Android SDK35 и JDK17+. Команды проверки приведены в README и42; на текущем ПК доступен `tools/verify-local.ps1`. GitHub —[romolego/spisok-ryadom](https://github.com/romolego/spisok-ryadom). Подтверждённая публикация фиксируется отдельной квитанцией в `docs/publications`.

## История: публикация0.29.0

Публикация завершена: [PR#6](https://github.com/romolego/spisok-ryadom/pull/6) слит обычным merge вmain; [v0.29.0](https://github.com/romolego/spisok-ryadom/releases/tag/v0.29.0) опубликован. Все7 файлов повторно скачаны без авторизации; размеры/SHA256 и CRC архивов совпали. Source commit `28fe1bbb8df78649e5671eb7b98d7077e25bfe73`, merge/tag `af57023f8bffcb018943986e115470b01486d14d`. Публичный source ZIP соответствует349 проверенным файлам с учётом CRLF/LF. Квитанция —`docs/publications/0.29.0.json`.

APK —`.local/artifacts/0.29.0/SpisokRyadom-0.29.0.apk`, код0.29.0(39)/Room26/ZIP24, рабочая папка прежняя.
