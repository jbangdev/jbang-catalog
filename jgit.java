///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.eclipse.jgit:org.eclipse.jgit.pgm:6.7.0.202309050840-r

import static java.lang.System.*;

public class jgit {

    public static void main(String... args) throws Exception {

        try {
            org.eclipse.jgit.pgm.Main.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
