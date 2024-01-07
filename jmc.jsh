//DEPS org.openjdk.jmc:common:8.3.1
//DEPS org.openjdk.jmc:flightrecorder:8.3.1
//DEPS org.openjdk.jmc:flightrecorder.rules:8.3.1
//DEPS org.openjdk.jmc:flightrecorder.rules.jdk:8.3.1

import org.openjdk.jmc.flightrecorder.*;
import org.openjdk.jmc.flightrecorder.jdk.*;
import static org.openjdk.jmc.flightrecorder.JfrLoaderToolkit.loadEvents;

import org.openjdk.jmc.common.*;
import org.openjdk.jmc.common.item.*;
import org.openjdk.jmc.common.unit.*;
import org.openjdk.jmc.flightrecorder.rules.*;
import org.openjdk.jmc.flightrecorder.rules.report.*;
import org.openjdk.jmc.flightrecorder.rules.report.html.*;
import static org.openjdk.jmc.common.unit.BinaryPrefix.NOBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.KIBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.MEBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.GIBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.TEBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.PEBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.EXBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.ZEBI;
import static org.openjdk.jmc.common.unit.BinaryPrefix.YOBI;
import static org.openjdk.jmc.common.unit.DecimalPrefix.YOCTO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.ZEPTO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.ATTO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.FEMTO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.PICO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.NANO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.MICRO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.MILLI;
import static org.openjdk.jmc.common.unit.DecimalPrefix.CENTI;
import static org.openjdk.jmc.common.unit.DecimalPrefix.DECI;
import static org.openjdk.jmc.common.unit.DecimalPrefix.NONE;
import static org.openjdk.jmc.common.unit.DecimalPrefix.DECA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.HECTO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.KILO;
import static org.openjdk.jmc.common.unit.DecimalPrefix.MEGA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.GIGA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.TERA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.PETA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.EXA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.ZETTA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.YOTTA;
import static org.openjdk.jmc.common.unit.DecimalPrefix.GIGA;
