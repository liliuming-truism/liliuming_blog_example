package top.truism.blog.leetcode;

public class ZigzagConversion {

    public static void main(String[] args) {
        ZigzagConversion zigzagConversion = new ZigzagConversion();
        System.out.println(zigzagConversion.convert("AB", 1));;
    }

    public String convert(String s, int numRows) {
        if (s == null || numRows == 1 || s.length() <= numRows) {
            return s;
        }

        StringBuilder[] rows = new StringBuilder[numRows];
        for (int i = 0; i < numRows; i++) {
            rows[i] = new StringBuilder();
        }
        int rowIndex = 0;
        int direction = 1; // 1表示向下移动，-1表示向上移动
        for (int i = 0; i < s.length(); i++) {
            rows[rowIndex].append(s.charAt(i));
            if (rowIndex == 0) {
                direction = 1;
            }else if (rowIndex == numRows - 1) {
                direction = -1;
            }
            rowIndex += direction;
        }
        StringBuilder res = new StringBuilder();
        for (StringBuilder row : rows) {
            res.append(row);
        }
        return res.toString();
    }

}
