package top.truism.blog.leetcode;

public class LongestPalindrome {

    public String longestPalindrome(String origin) {
        if (origin == null || origin.length() < 2) {
            return origin;
        }
        int maxLen = 1;
        int start = 0;
        int end = 0;
        for (int i = 0; i < origin.length(); i++) {
            int len1 = expandFromOrigin(origin, i, i);

            int len2 = expandFromOrigin(origin, i, i + 1);

            int len = Math.max(len1, len2);

            if (len > maxLen) {
                start = i - (len - 1) / 2;
                end = i + len / 2;
            }
        }
        return origin.substring(start, end + 1);
    }

    private int expandFromOrigin(String origin, int left, int right) {
        if (left >= 0 && left < origin.length() && origin.charAt(left) == origin.charAt(right)) {
            left--;
            right++;
        }
        return right - left - 1;
    }

}
