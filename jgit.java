///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.eclipse.jgit:org.eclipse.jgit.pgm:5.13.2.202306221912-r

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
