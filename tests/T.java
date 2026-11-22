package pl.stashreader;

/** Tiny test runner, no libraries. A failed check prints one line; any failure exits non-zero. */
public final class T {
    static int fails = 0, runs = 0;

    static void eq(String name, Object want, Object got) {
        runs++;
        boolean ok = want == null ? got == null : want.equals(got);
        if (!ok) { fails++; System.out.println("FAIL " + name + ": wanted <" + want + "> got <" + got + ">"); }
    }
    static void yes(String name, boolean cond) { eq(name, true, cond); }

    public static void main(String[] a) throws Exception {
        yes("the runner works", 1 + 1 == 2);
        System.out.println(runs + " checks, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
