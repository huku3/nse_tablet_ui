package jp.co.nse.worker.ui.theme

import jp.co.nse.worker.R

/** マイページアイコンのプリセット（動物イラスト）。サーバーのavatar_keyに対応するキーを持つ */
data class AvatarPreset(val key: String, val label: String, val drawableRes: Int)

val AppAvatarPresets = listOf(
    AvatarPreset("panda", "パンダ", R.drawable.avatar_animal_panda),
    AvatarPreset("tiger", "トラ", R.drawable.avatar_animal_tiger),
    AvatarPreset("giraffe", "キリン", R.drawable.avatar_animal_giraffe),
    AvatarPreset("elephant", "ゾウ", R.drawable.avatar_animal_elephant),
    AvatarPreset("deer", "シカ", R.drawable.avatar_animal_deer),
    AvatarPreset("hedgehog", "ハリネズミ", R.drawable.avatar_animal_hedgehog),
    AvatarPreset("wolf", "オオカミ", R.drawable.avatar_animal_wolf),
    AvatarPreset("zebra", "シマウマ", R.drawable.avatar_animal_zebra),
    AvatarPreset("hippo", "カバ", R.drawable.avatar_animal_hippo),
    AvatarPreset("rhino", "サイ", R.drawable.avatar_animal_rhino),
    AvatarPreset("rabbit", "ウサギ", R.drawable.avatar_animal_rabbit),
    AvatarPreset("hamster", "ハムスター", R.drawable.avatar_animal_hamster),
    AvatarPreset("cat", "ネコ", R.drawable.avatar_animal_cat),
    AvatarPreset("dog", "イヌ", R.drawable.avatar_animal_dog),
    AvatarPreset("pig", "ブタ", R.drawable.avatar_animal_pig),
    AvatarPreset("octopus", "タコ", R.drawable.avatar_animal_octopus),
    AvatarPreset("jellyfish", "クラゲ", R.drawable.avatar_animal_jellyfish),
    AvatarPreset("seahorse", "タツノオトシゴ", R.drawable.avatar_animal_seahorse),
    AvatarPreset("chameleon", "カメレオン", R.drawable.avatar_animal_chameleon),
    AvatarPreset("frog", "カエル", R.drawable.avatar_animal_frog),
    AvatarPreset("flamingo", "フラミンゴ", R.drawable.avatar_animal_flamingo),
    AvatarPreset("toucan", "オオハシ", R.drawable.avatar_animal_toucan),
    AvatarPreset("hummingbird", "ハチドリ", R.drawable.avatar_animal_hummingbird),
    AvatarPreset("cockatoo", "オウム", R.drawable.avatar_animal_cockatoo),
)

fun avatarDrawableForKey(key: String?): Int? = AppAvatarPresets.firstOrNull { it.key == key }?.drawableRes
