package name.modid.client.ore;

public enum OreType {
    COAL("Coal Ore",              20, 17,   0, 256, 0xBBBBBB, 1001L),
    DEEPSLATE_COAL("Deepslate Coal Ore",    20, 17, -64,   8, 0x777777, 1002L),
    IRON("Iron Ore",              20,  9, -24,  80, 0xD8AF93, 1003L),
    DEEPSLATE_IRON("Deepslate Iron Ore",    15,  9, -64,   8, 0xAA7755, 1004L),
    COPPER("Copper Ore",          16, 10,   0, 112, 0xFF7733, 1005L),
    DEEPSLATE_COPPER("Deepslate Copper Ore",10, 10, -16,   8, 0xCC5522, 1006L),
    GOLD("Gold Ore",               4,  9, -64,  32, 0xFFD700, 1007L),
    DEEPSLATE_GOLD("Deepslate Gold Ore",     6,  9, -64,   8, 0xCCAA00, 1008L),
    LAPIS("Lapis Ore",             2,  7, -32,  64, 0x1E3DFF, 1009L),
    DEEPSLATE_LAPIS("Deepslate Lapis Ore",   3,  7, -64,   8, 0x0A1FCC, 1010L),
    REDSTONE("Redstone Ore",       8,  8, -64,  16, 0xFF2200, 1011L),
    DEEPSLATE_REDSTONE("Deepslate Redstone Ore", 8, 8, -64, 8, 0xCC0000, 1012L),
    DIAMOND("Diamond Ore",         2,  8, -64,  16, 0x00FFFF, 1013L),
    DEEPSLATE_DIAMOND("Deepslate Diamond Ore", 3, 8, -64,  8, 0x00BBBB, 1014L),
    EMERALD("Emerald Ore",         3,  3, -16, 256, 0x00FF00, 1015L),
    DEEPSLATE_EMERALD("Deepslate Emerald Ore", 2, 3, -64,  8, 0x00BB00, 1016L),
    // Nether ores — bright colors so they don't blend with red/dark netherrack.
    ANCIENT_DEBRIS("Ancient Debris", 1,  3,   8, 119, 0xFFFFFF, 1017L),
    NETHER_QUARTZ("Nether Quartz Ore", 16, 14, 10, 117, 0xFF66FF, 1018L),
    NETHER_GOLD("Nether Gold Ore",     10, 10, 10, 117, 0xFFEE33, 1019L);

    public final String displayName;
    public final int veinsPerChunk;
    public final int veinSize;
    public final int minY;
    public final int maxY;
    public final int color;
    public final long salt;

    OreType(String displayName, int veinsPerChunk, int veinSize,
            int minY, int maxY, int color, long salt) {
        this.displayName = displayName;
        this.veinsPerChunk = veinsPerChunk;
        this.veinSize = veinSize;
        this.minY = minY;
        this.maxY = maxY;
        this.color = color;
        this.salt = salt;
    }

    public float r() { return ((color >> 16) & 0xFF) / 255.0f; }
    public float g() { return ((color >>  8) & 0xFF) / 255.0f; }
    public float b() { return  (color        & 0xFF) / 255.0f; }

    public String configKey() { return name().toLowerCase(); }

    /** Returns the deepslate variant for a non-deepslate ore, or null if none exists. */
    public OreType deepslateVariant() {
        return switch (this) {
            case COAL     -> DEEPSLATE_COAL;
            case IRON     -> DEEPSLATE_IRON;
            case COPPER   -> DEEPSLATE_COPPER;
            case GOLD     -> DEEPSLATE_GOLD;
            case LAPIS    -> DEEPSLATE_LAPIS;
            case REDSTONE -> DEEPSLATE_REDSTONE;
            case DIAMOND  -> DEEPSLATE_DIAMOND;
            case EMERALD  -> DEEPSLATE_EMERALD;
            default       -> null;
        };
    }
}
