package ru.lionzxy.tplauncher.ui

object Strings {
    const val serverAddress = "games.glitchless.ru"
    const val login = "Логин"
    const val password = "Пароль"
    const val server = "Сервер"
    const val launchSettings = "Настройки запуска"
    const val registerOnSite = "Регистрация на сайте"
    const val enterGame = "Войти в игру"
    const val loggedIn = "Вход осуществлен"
    const val enterLoginAndPassword = "Введите логин и пароль"
    const val goodGame = "Приятной игры"
    const val loadingGame = "Загружаем игру..."
    const val launchingMinecraft = "Запускаем Minecraft..."
    const val enterValidEmail = "Введите валидную почту"
    const val passwordCannotBeEmpty = "Пароль не может быть пустым"
    const val checkInternetConnection = "Проверьте подключение к интернету"
    const val internalError = "Внутреняя ошибка, мы уже исправляем это"
    const val connectionBlocked =
        "Похоже, антивирус, файрвол или VPN блокирует подключение лаунчера к сети. " +
            "Разрешите доступ в интернет для java.exe и javaw.exe лаунчера в вашем антивирусе " +
            "и в брандмауэре Windows, затем повторите попытку."
    const val memorySize = "Объем памяти"
    const val javaParams = "Параметры java"
    const val prefix = "Prefix"
    const val debugMode = "Дебаг-режим"
    const val autoJoinServer = "Авто-заход на сервер"
    const val showLogs = "Показать логи"
    const val uiScale = "Масштаб интерфейса"
    const val parallelDownloads = "Параллельных загрузок"
    const val logsTitle = "Логи"
    const val scrollToEnd = "В конец"
    const val copyLogs = "Копировать"
    const val saveLogs = "Сохранить"
    const val saveLogDialogTitle = "Сохранить лог"
    const val goToGameDirectory = "Перейти в директорию игры"
    const val logout = "Выйти из аккаунта"
    const val deleteGameAndReset = "Удалить игру и сбросить настройки лаунчера"
    const val back = "Вернуться"
    const val apply = "Применить"
    const val allowAccess = "Разрешить доступ"
    const val retry = "Повторить"
    const val uacDeclined =
        "Вы отклонили запрос прав администратора, поэтому исправление не применено. " +
            "Нажмите «Разрешить доступ», чтобы попробовать снова, или «Повторить», " +
            "чтобы запустить уже установленную игру."
    const val firewallFixDidNotHelp =
        "Правило брандмауэра добавлено, но сеть всё ещё заблокирована. " +
            "Разрешите java.exe и javaw.exe лаунчера в вашем антивирусе, затем нажмите «Повторить»."
    const val notInstalledOffline =
        "Модпак ещё не установлен, а сеть недоступна или заблокирована. " +
            "Для первой установки нужно рабочее подключение к интернету."
    const val drwebFirewallGuidance =
        "Dr.Web блокирует подключение лаунчера к сети. Откройте Dr.Web → нажмите значок замка " +
            "(режим администратора) → Брандмауэр → «Параметры работы приложений» → найдите java.exe и " +
            "javaw.exe лаунчера и установите «Разрешать всё», затем нажмите «Повторить». " +
            "(Добавления только в «Исключения» может быть недостаточно.)"

    fun thirdPartyAvGuidance(products: String) =
        "$products блокирует подключение лаунчера к сети. Разрешите java.exe и javaw.exe лаунчера " +
            "в брандмауэре/контроле приложений вашего антивируса, затем нажмите «Повторить»."

    fun authByEmail(email: String) = "Авторизация по email $email..."
    fun clearBackup(size: String) = "Очистить папку с бекапом ($size)"
}
