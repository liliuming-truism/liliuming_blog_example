package top.truism.blog.leetcode;

import java.util.List;

/**
 * 请你来实现一个 myAtoi(string s) 函数，使其能将字符串转换成一个 32 位有符号整数。
 * <p>
 * 函数 myAtoi(string s) 的算法如下：
 * <p>
 * 空格：读入字符串并丢弃无用的前导空格（" "）
 * 符号：检查下一个字符（假设还未到字符末尾）为 '-' 还是 '+'。如果两者都不存在，则假定结果为正。
 * 转换：通过跳过前置零来读取该整数，直到遇到非数字字符或到达字符串的结尾。如果没有读取数字，则结果为0。
 * 舍入：如果整数数超过 32 位有符号整数范围 [−231,  231 − 1] ，需要截断这个整数，使其保持在这个范围内。具体来说，小于 −231 的整数应该被舍入为 −231 ，大于 231 − 1 的整数应该被舍入为 231 − 1 。
 * 返回整数作为最终结果。
 */
public class ConvertStringToInteger {

    private static final List<Character> nums = List.of('1', '2', '3', '4', '5', '6', '7', '8', '9', '0');

    public static void main(String[] args) {
        ConvertStringToInteger convertStringToInteger = new ConvertStringToInteger();
        System.out.println(convertStringToInteger.myAtoi("42"));

        System.out.println(convertStringToInteger.myAtoi("   -042"));

        System.out.println(convertStringToInteger.myAtoi("0-1"));

        System.out.println(convertStringToInteger.myAtoi("words and 987"));

        System.out.println(convertStringToInteger.myAtoi("20000000000000000000"));

    }

    public int myAtoi(String s) {
        s = s.trim();
        if (s.isEmpty()) {
            return 0;
        }
        boolean plus = true;
        if (s.charAt(0) == '-') {
            plus = false;
            s = s.substring(1);
        } else if (s.charAt(0) == '+') {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return 0;
        }
        s = removeZero(s);
        if (s.isEmpty()) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char charI = s.charAt(i);
            if (nums.contains(charI)) {
                sb.append(charI);
            } else {
                break;
            }
        }
        if (sb.isEmpty()) {
            return 0;
        }
        try {
            int val = Integer.parseInt(sb.toString());
            if (!plus) {
                return -val;
            }
            return val;
        } catch (NumberFormatException e) {
            if (plus) {
                return Integer.MAX_VALUE;
            } else {
                return Integer.MIN_VALUE;
            }
        }
    }

    private String removeZero(String s) {
        if (s.isEmpty()) {
            return "";
        }
        if (s.charAt(0) == '0') {
            return removeZero(s.substring(1));
        }
        return s;
    }
}
