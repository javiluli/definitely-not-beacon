import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Resume CPU y asignaciones de un JFR, distinguiendo preparación y ventanas medidas. */
class ReadBeaconJfr {
    record Window(String label, Instant start, Instant end) {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java tools/profiling/ReadBeaconJfr.java <recording.jfr>");
        Path path = Path.of(args[0]);
        List<Window> windows = new ArrayList<>();
        try (RecordingFile file = new RecordingFile(path)) {
            while (file.hasMoreEvents()) {
                var event = file.readEvent();
                if (event.getEventType().getName().equals("beacon.ProfilePhase")) {
                    windows.add(new Window(event.getString("scenario") + "/" + event.getString("phase"), event.getStartTime(), event.getEndTime()));
                }
            }
        }
        Map<String, Long> all = new HashMap<>(), measured = new HashMap<>();
        long samples = 0, allocations = 0;
        try (RecordingFile file = new RecordingFile(path)) {
            while (file.hasMoreEvents()) {
                var event = file.readEvent();
                String type = event.getEventType().getName();
                boolean allocation = type.equals("jdk.ObjectAllocationSample");
                if (!allocation && !type.equals("jdk.ExecutionSample")) continue;
                if (allocation) allocations++; else samples++;
                var stack = event.getStackTrace();
                if (stack == null) continue;
                for (var frame : stack.getFrames()) {
                    String owner = frame.getMethod().getType().getName();
                    if (!owner.startsWith("dev.hytalemodding.") || owner.contains("Probe") || owner.contains("SmokePlugin") || owner.contains("ProfilePlugin")) continue;
                    String site = owner.substring(owner.lastIndexOf('.') + 1) + "." + frame.getMethod().getName();
                    String category = allocation ? "allocationWeight" : "cpuSamples";
                    long value = allocation ? event.getLong("weight") : 1L;
                    all.merge(category + "," + site, value, Long::sum);
                    for (var window : windows) {
                        if (!event.getStartTime().isBefore(window.start()) && !event.getStartTime().isAfter(window.end())) {
                            measured.merge(category + "," + window.label() + "," + site, value, Long::sum);
                            break;
                        }
                    }
                    break;
                }
            }
        }
        System.out.println("recording=" + path + "; windows=" + windows.size() + "; cpuSamples=" + samples + "; allocationSamples=" + allocations);
        System.out.println("ALL RECORDED WORK (including preparation/warmup)");
        all.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(20)
                .forEach(e -> System.out.println(e.getKey() + "," + e.getValue()));
        System.out.println("MEASURED WINDOWS ONLY");
        measured.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(30)
                .forEach(e -> System.out.println(e.getKey() + "," + e.getValue()));
    }
}
