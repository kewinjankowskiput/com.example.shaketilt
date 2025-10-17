package com.example.shaketilt

data class Level(
    val id: Int,
    val width: Float,
    val height: Float,
    val ballStartX: Float,
    val ballStartY: Float,
    val flagX: Float,
    val flagY: Float,
    val spikes: List<Spike> = emptyList(),
    val platforms: List<PolygonPlatform> = emptyList(),
    val rotatingPlatforms: List<RotatingPlatform> = emptyList()
) {
    fun allPlatforms(): List<PolygonPlatform> {
        val rotated = rotatingPlatforms.map { PolygonPlatform(it.getRotatedVertices(), it.gradientId) }
        return platforms + rotated
    }

    fun isBallAtFlag(ballX: Float, ballY: Float, radius: Float = 50f): Boolean {
        val dx = ballX - flagX
        val dy = ballY - flagY
        return dx * dx + dy * dy <= radius * radius
    }

    companion object {
        val levels: List<Level> = listOf(
            Level(
                id = 1,
                width = 6400f,
                height = 1080f,
                ballStartX = 300f,
                ballStartY = 700f,
                flagX = 5950f,
                flagY = 400f,
                spikes = listOf(
                    Spike(600f, 2000f)
                ),
                platforms = listOf(
                    PolygonPlatform(
                        listOf(26f to 1083f, 142f to 807f, 346f to 852f, 612f to 832f, 844f to 900f, 1119f to 1082f),
                        gradientId = 3
                    ),
                    PolygonPlatform(
                        listOf(1260f to 1080f, 1168f to 890f, 1364f to 928f, 1508f to 898f, 1394f to 688f, 1710f to 698f, 1846f to 806f, 2058f to 850f, 2172f to 752f, 2182f to 1096f),
                        gradientId = 3
                    ),
                    PolygonPlatform(
                        listOf(2314f to 734f, 2580f to 734f, 2576f to 864f, 2308f to 826f),
                        gradientId = 0
                    ),
                    PolygonPlatform(
                        listOf(2462f to 1090f, 2486f to 1002f, 3066f to 1008f, 3360f to 984f, 3698f to 976f, 3700f to 438f,
                            4362f to 386f, 4374f to 510f, 4004f to 532f, 4006f to 942f, 4394f to 956f, 4278f to 1090f),
                        gradientId = 3
                    ),
                    PolygonPlatform(
                        listOf(3260f to 472f, 3262f to 582f, 3066f to 572f, 3066f to 458f),
                        gradientId = 2
                    ),
                    PolygonPlatform(
                        listOf(2782f to 730f, 2778f to 844f, 3062f to 860f, 3062f to 726f),
                        gradientId = 1
                    ),
                    PolygonPlatform(
                        listOf(3272f to 744f, 3488f to 738f, 3496f to 822f, 3270f to 824f),
                        gradientId = 0
                    ),
                    PolygonPlatform(
                        listOf(4546f to 0f, 4582f to 618f, 4322f to 656f, 4222f to 740f, 4744f to 716f, 4704f to -10f),
                        gradientId = 2
                    ),
                    PolygonPlatform(
                        listOf(4534f to 932f, 4576f to 1090f, 6038f to 1100f, 5880f to 480f, 5388f to 848f),
                        gradientId = 0
                    )
                ),
                rotatingPlatforms = listOf(
                    RotatingPlatform(
                        baseVertices = listOf(1000f to 9000f, 1200f to 9000f, 1200f to 9200f, 1000f to 9200f),
                        pivot = 1100f to 910f,
                        angularSpeed = 1f,
                        gradientId = 2
                    )
                )
            ),
            Level(
                id = 2,
                width = 8000f,
                height = 1600f,
                ballStartX = 350f,
                ballStartY = 350f,
                flagX = 6800f,
                flagY = 300f,
                spikes = listOf(
                    Spike(3890f, 768f),
                    Spike(3960f, 768f),
                    Spike(4040f, 768f),
                    Spike(1368f, 864f)
                ),
                platforms = listOf(
                    PolygonPlatform(
                        listOf(146f to 1631f, 166f to 551f, 606f to 680f, 784f to 674f, 802f to 898f, 1998f to 886f,
                            2020f to 672f, 2204f to 658f, 2214f to 1614f),
                        gradientId = 0
                    ),
                    PolygonPlatform(
                        listOf(3158f to 1610f, 3262f to 1244f, 3584f to 1140f, 3586f to 1334f, 5270f to 1334f, 5282f to 1600f),
                        gradientId = 0
                    ),
                    PolygonPlatform(
                        listOf(5108f to 874f, 5978f to 870f, 5994f to 1604f, 5982f to 1252f, 7358f to 1276f, 7750f to 1600f,
                            5436f to 1600f),
                        gradientId = 2
                    )
                ),
                rotatingPlatforms = listOf(
                    RotatingPlatform(
                        baseVertices = listOf(1080f to 830f, 1120f to 830f, 1120f to 530f, 1080f to 530f),
                        pivot = 1100f to 680f,
                        angularSpeed = 1f,
                        gradientId = 2
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(1450f to 660f, 1450f to 700f, 1750f to 700f, 1750f to 660f),
                        pivot = 1600f to 680f,
                        angularSpeed = 1f,
                        gradientId = 2
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(2480f to 830f, 2520f to 830f, 2520f to 530f, 2480f to 530f),
                        pivot = 2500f to 680f,
                        angularSpeed = 1f,
                        gradientId = 2
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(2850f to 660f, 2850f to 700f, 3150f to 700f, 3150f to 660f),
                        pivot = 3000f to 680f,
                        angularSpeed = 1f,
                        gradientId = 2
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(4080f to 1300f, 4120f to 1300f, 4120f to 700f, 4080f to 700f),
                        pivot = 4100f to 1000f,
                        angularSpeed = 1.5f,
                        gradientId = 2
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(4980f to 800f, 5020f to 800f, 5020f to 1300f, 4980f to 1300f),
                        pivot = 5000f to 1050f,
                        angularSpeed = 5f,
                        gradientId = 2
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(6580f to 400f, 6620f to 400f, 6620f to 600f, 6580f to 600f),
                        pivot = 6600f to 800f,
                        angularSpeed = 4f,
                        gradientId = 4
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(6400f to 780f, 6200f to 780f, 6200f to 820f, 6400f to 820f),
                        pivot = 6600f to 800f,
                        angularSpeed = 4f,
                        gradientId = 4
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(6580f to 1000f, 6620f to 1000f, 6620f to 1200f, 6580f to 1200f),
                        pivot = 6600f to 800f,
                        angularSpeed = 4f,
                        gradientId = 4
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(6800f to 780f, 7000f to 780f, 7000f to 820f, 6800f to 820f),
                        pivot = 6600f to 800f,
                        angularSpeed = 4f,
                        gradientId = 4
                    )
                )
            ),

            Level(
                id = 3,
                width = 10000f,
                height = 8000f,
                ballStartX = 300f,
                ballStartY = 450f,
                flagX = 9600f,
                flagY = 7900f,
                spikes = listOf(
                    Spike(1500f, 1046f),
                    Spike(2784f, 1674f),
                    Spike(3680f, 2034f),
                    Spike(3516f, 1802f),
                    Spike(4406f, 2358f),
                    Spike(4476f, 2378f),
                    Spike(5248f, 2528f),
                    Spike(5924f, 2770f),
                    Spike(7434f, 3276f),
                    Spike(7458f, 3218f),
                    Spike(9442f, 3600f)
                ),
                platforms = listOf(
                    PolygonPlatform(listOf(0f to 10000f, 166f to 531f, 374f to 531f, 1470f to 1066f, 2916f to 1750f,
                        4284f to 2340f, 5704f to 2628f, 7128f to 3230f, 8402f to 3506f, 9478f to 3626f, 9708f to 3534f,
                        9434f to 4642f, 8000f to 10000f

                        ), gradientId = 2)
                ),
                rotatingPlatforms = listOf(
                    RotatingPlatform(
                        baseVertices = listOf(9876f-20f to 4474f+200f, 9876f+20f to 4474f+200f, 9876f+20f to 4474f-200f, 9876f-20f to 4474f-200f),
                        pivot = 9876f to 4474f,
                        angularSpeed = 6f,
                        gradientId = 1
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(9610f-20f to 5124f+200f, 9610f+20f to 5124f+200f, 9610f+20f to 5124f-200f, 9610f-20f to 5124f-200f),
                        pivot = 9610f to 5124f,
                        angularSpeed = 6f,
                        gradientId = 1
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(9958f-20f to 5494f+200f, 9958f+20f to 5494f+200f, 9958f+20f to 5494f-200f, 9958f-20f to 5494f-200f),
                        pivot = 9958f to 5494f,
                        angularSpeed = 6f,
                        gradientId = 1
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(9456f-20f to 6164f+200f, 9456f+20f to 6164f+200f, 9456f+20f to 6164f-200f, 9456f-20f to 6164f-200f),
                        pivot = 9456f to 6164f,
                        angularSpeed = 6f,
                        gradientId = 1
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(9938f-20f to 7038f+200f, 9938f+20f to 7038f+200f, 9938f+20f to 7038f-200f, 9938f-20f to 7038f-200f),
                        pivot = 9938f to 7038f,
                        angularSpeed = 6f,
                        gradientId = 1
                    ),
                    RotatingPlatform(
                        baseVertices = listOf(9800f-20f to 7960f+200f, 9800f+20f to 7960f+200f, 9800f+20f to 7960f-200f, 9800f-20f to 7960f-200f),
                        pivot = 9800f to 7960f,
                        angularSpeed = 6f,
                        gradientId = 1
                    )
                )
            )

            // Add more levels here
        )

        fun getLevelById(id: Int): Level {
            return levels.firstOrNull { it.id == id } ?: levels.first()
        }
    }
}