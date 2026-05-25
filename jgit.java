///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.eclipse.jgit:org.eclipse.jgit.pgm:7.4.0.202509020913-r

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
