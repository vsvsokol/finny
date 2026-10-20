package ru.finney.pet.ui.sound

import androidx.annotation.RawRes
import ru.finney.pet.R

/**
 * Все звуки игры. Файлы — `res/raw/`, исходники и таблица — design/exports/sounds/.
 *
 * Звука, которого звуковик ещё не прислал, в res/raw лежит заглушка с тем же именем
 * (tools/make_sound_stubs.py). Настоящий файл кладётся поверх — код не меняется.
 */
enum class Sfx(@RawRes val res: Int) {
    // Интерфейс
    Tap(R.raw.sfx_ui_tap),
    Back(R.raw.sfx_ui_back),
    Denied(R.raw.sfx_ui_denied),

    // Деньги
    Coin(R.raw.sfx_coin),
    Purchase(R.raw.sfx_purchase),
    PiggyIn(R.raw.sfx_piggy_in),
    PiggyOut(R.raw.sfx_piggy_out),

    // Мини-игры и итоги
    Correct(R.raw.sfx_game_correct),
    Wrong(R.raw.sfx_game_wrong),
    GameWin(R.raw.sfx_game_win),
    GameFail(R.raw.sfx_game_fail),
    LevelUp(R.raw.sfx_level_up),
    Pour(R.raw.sfx_pour),

    // Питомец и уход
    PetHappy(R.raw.sfx_pet_happy),
    PetSad(R.raw.sfx_pet_sad),
    PetEat(R.raw.sfx_pet_eat),
    FoodThrow(R.raw.sfx_food_throw),
    WashBubble(R.raw.sfx_wash_bubble),
    WashClean(R.raw.sfx_wash_clean),
    SleepLoop(R.raw.sfx_pet_sleep_loop),

    // Комната
    CapsuleDoor(R.raw.sfx_capsule_door),
    /** Питомец прыжком уходит в соседнюю комнату и впрыгивает в неё (RoomScene). */
    PetLeap(R.raw.sfx_pet_leap),
    PetLand(R.raw.sfx_pet_land),
    Ufo(R.raw.sfx_ufo),

    // Игрушки: взял, уронил и у каждой своя встряска (см. toyShakeSound в ToyPlay.kt)
    ToyPickup(R.raw.sfx_toy_pickup),
    ToyDrop(R.raw.sfx_toy_drop),
    ToyBounce(R.raw.sfx_toy_bounce),
    ToySqueak(R.raw.sfx_toy_squeak),
    ToyClick(R.raw.sfx_toy_click),
    ToyRustle(R.raw.sfx_toy_rustle),
}

/**
 * Фоновая музыка. Своих файлов у игр и знакомства пока нет — играет тема комнаты.
 * Когда появятся mus_games_loop и mus_setup_loop, поменять здесь ссылку.
 */
enum class Music(@RawRes val res: Int) {
    Room(R.raw.mus_room_loop),
    Games(R.raw.mus_room_loop),
    Setup(R.raw.mus_room_loop),
}
