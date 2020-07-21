//usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>

import static java.lang.System.*;

import java.util.Arrays;
import java.util.stream.Collectors;

public class hello {

    public static void main(String... args) {
        out.println("Hello " + Arrays.stream(args).collect(Collectors.joining(" ")));
    }
}
