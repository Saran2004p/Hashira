import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

/**
 * Shamir-style secret reconstruction with outlier detection.
 * Reads JSON from stdin (assignment format), decodes base-N values,
 * computes f(0) via Lagrange interpolation (exact rational arithmetic),
 * and prints the secret and any outlier indices.
 *
 * Usage:
 *  javac ShamirSolver.java
 *  java ShamirSolver < input.json
 */
public class ShamirSolver {
    static class Frac {
        BigInteger num;
        BigInteger den;

        Frac(BigInteger n, BigInteger d) {
            if (d.signum() == 0) throw new ArithmeticException("zero denominator");
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            if (!g.equals(BigInteger.ONE)) {
                n = n.divide(g);
                d = d.divide(g);
            }
            this.num = n;
            this.den = d;
        }

        static Frac of(BigInteger v) { return new Frac(v, BigInteger.ONE); }

        Frac add(Frac other) {
            BigInteger n = this.num.multiply(other.den).add(other.num.multiply(this.den));
            BigInteger d = this.den.multiply(other.den);
            return new Frac(n, d);
        }

        Frac sub(Frac other) {
            BigInteger n = this.num.multiply(other.den).subtract(other.num.multiply(this.den));
            BigInteger d = this.den.multiply(other.den);
            return new Frac(n, d);
        }

        Frac mul(Frac other) {
            return new Frac(this.num.multiply(other.num), this.den.multiply(other.den));
        }

        Frac div(Frac other) {
            if (other.num.signum() == 0) throw new ArithmeticException("division by zero");
            return new Frac(this.num.multiply(other.den), this.den.multiply(other.num));
        }

        public boolean equals(Object o) {
            if (!(o instanceof Frac)) return false;
            Frac f = (Frac)o;
            return this.num.equals(f.num) && this.den.equals(f.den);
        }

        public String toString() {
            if (den.equals(BigInteger.ONE)) return num.toString();
            return num.toString() + "/" + den.toString();
        }
    }

    static class Share {
        int idx;        // index key (like 1,2,3,6)
        BigInteger x;   // x-value (x = idx)
        BigInteger y;   // decoded y-value
        Share(int idx, BigInteger y) { this.idx = idx; this.x = BigInteger.valueOf(idx); this.y = y; }
    }

    static class InputData {
        int n;
        int k;
        List<Share> shares = new ArrayList<>();
    }

    static InputData parseJson(String s) {
        InputData out = new InputData();

        Pattern keysPat = Pattern.compile("\"keys\"\\s*:\\s*\\{([^}]*)\\}", Pattern.DOTALL);
        Matcher mkeys = keysPat.matcher(s);
        if (mkeys.find()) {
            String inner = mkeys.group(1);
            Pattern nPat = Pattern.compile("\"n\"\\s*:\\s*(\\d+)");
            Pattern kPat = Pattern.compile("\"k\"\\s*:\\s*(\\d+)");
            Matcher mn = nPat.matcher(inner);
            Matcher mk = kPat.matcher(inner);
            if (mn.find()) out.n = Integer.parseInt(mn.group(1));
            if (mk.find()) out.k = Integer.parseInt(mk.group(1));
        } else {
            throw new IllegalArgumentException("Missing keys object");
        }

        Pattern entryPat = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{([^}]*)\\}", Pattern.DOTALL);
        Matcher me = entryPat.matcher(s);
        while (me.find()) {
            String key = me.group(1);
            if (key.equals("1") || key.equals("2") || key.equals("3") || true) {
                // skip "keys" is already excluded because it is not a numeric key
            }
            int idx = Integer.parseInt(key);
            String body = me.group(2);
            Pattern basePat = Pattern.compile("\"base\"\\s*:\\s*\"([^\"]+)\"");
            Pattern valPat = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
            Matcher mb = basePat.matcher(body);
            Matcher mv = valPat.matcher(body);
            if (mb.find() && mv.find()) {
                String baseStr = mb.group(1).trim();
                String valStr = mv.group(1).trim();
                int base = Integer.parseInt(baseStr);
                BigInteger y = new BigInteger(valStr, base);
                out.shares.add(new Share(idx, y));
            }
        }

        if (out.shares.size() != out.n) {
            out.n = out.shares.size();
        }
        return out;
    }

    static Frac lagrangeConstant(List<Share> pts) {
        int k = pts.size();
        Frac total = Frac.of(BigInteger.ZERO);
        Frac[] ys = new Frac[k];
        BigInteger[] xs = new BigInteger[k];
        for (int i = 0; i < k; ++i) {
            xs[i] = pts.get(i).x;
            ys[i] = Frac.of(pts.get(i).y);
        }
        for (int i = 0; i < k; ++i) {
            Frac num = Frac.of(BigInteger.ONE);
            Frac den = Frac.of(BigInteger.ONE);
            for (int j = 0; j < k; ++j) {
                if (i == j) continue;
                // multiply num *= (0 - xj) => -xj
                num = num.mul(Frac.of(xs[j].negate()));
                // den *= (xi - xj)
                den = den.mul(Frac.of(xs[i].subtract(xs[j])));
            }
            Frac Li0 = num.div(den);
            total = total.add(ys[i].mul(Li0));
        }
        return total;
    }

    static void combinationsRec(int n, int k, int start, int[] comb, List<int[]> out) {
        if (k == 0) {
            out.add(comb.clone());
            return;
        }
        for (int i = start; i <= n - k; ++i) {
            comb[comb.length - k] = i;
            combinationsRec(n, k - 1, i + 1, comb, out);
        }
    }

    static List<int[]> combinations(int n, int k) {
        List<int[]> out = new ArrayList<>();
        combinationsRec(n, k, 0, new int[k], out);
        return out;
    }

    static Map<String,Object> consensusSecret(List<Share> shares, int k) {
        int n = shares.size();
        List<int[]> combos = combinations(n, k);
        Frac bestSecret = null;
        Set<Integer> bestInliers = new HashSet<>();

        for (int[] combo : combos) {
            List<Share> combShares = new ArrayList<>();
            for (int idx : combo) combShares.add(shares.get(idx));
            Frac s;
            try {
                s = lagrangeConstant(combShares);
            } catch (ArithmeticException ex) {
                continue;
            }
            Set<Integer> inliers = new HashSet<>();
            for (int idx : combo) inliers.add(idx);
            for (int r = 0; r < n; ++r) {
                if (inliers.contains(r)) continue;
                boolean consistent = false;
                for (int drop = 0; drop < k && !consistent; ++drop) {
                    List<Share> test = new ArrayList<>();
                    for (int j = 0; j < k; ++j) {
                        if (j == drop) continue;
                        test.add(shares.get(combo[j]));
                    }
                    test.add(shares.get(r));
                    try {
                        Frac s2 = lagrangeConstant(test);
                        if (s2.equals(s)) consistent = true;
                    } catch (ArithmeticException e) {
                        
                    }
                }
                if (consistent) inliers.add(r);
            }

            if (bestSecret == null || inliers.size() > bestInliers.size()) {
                bestSecret = s;
                bestInliers = inliers;
            }
        }

        // result
        Map<String,Object> res = new HashMap<>();
        res.put("secret", bestSecret);
        List<Integer> inlierIdx = new ArrayList<>();
        for (int idx : bestInliers) inlierIdx.add(shares.get(idx).idx);
        Collections.sort(inlierIdx);
        res.put("inliers", inlierIdx);

        List<Integer> outliers = new ArrayList<>();
        for (int i = 0; i < shares.size(); ++i) {
            if (!bestInliers.contains(i)) outliers.add(shares.get(i).idx);
        }
        Collections.sort(outliers);
        res.put("outliers", outliers);
        return res;
    }

    public static void main(String[] argv) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        String input = sb.toString().trim();
        if (input.isEmpty()) {
            System.err.println("No input provided. Provide JSON on stdin.");
            return;
        }

        InputData data = parseJson(input);
        if (data.k < 2) {
            System.err.println("k must be >= 2");
            return;
        }
        if (data.shares.size() < data.k) {
            System.err.println("Not enough shares provided: need k=" + data.k + ", got " + data.shares.size());
            return;
        }

        Map<String,Object> ans = consensusSecret(data.shares, data.k);
        Frac secret = (Frac)ans.get("secret");
        @SuppressWarnings("unchecked")
        List<Integer> outliers = (List<Integer>)ans.get("outliers");
        @SuppressWarnings("unchecked")
        List<Integer> inliers = (List<Integer>)ans.get("inliers");

        System.out.println("{");
        System.out.println("  \"secret\": \"" + secret.toString() + "\",");
        System.out.println("  \"inliers\": " + inliers.toString() + ",");
        System.out.println("  \"outliers\": " + outliers.toString());
        System.out.println("}");
    }
}
