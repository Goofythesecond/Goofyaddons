package com.goofy.goofyaddons.features.bookflipper.helper;

public record Book(String id, int level, int sellLevel, String name) {


    public String getLevel(int i) {
        return this.id + "_" + i;
    }

    public String getRomanLevel(int i) {
        return name + " " + toRoman(i);
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    public int getQtyAmount(int level) {
        return (1 << (sellLevel - level));
    }

}
