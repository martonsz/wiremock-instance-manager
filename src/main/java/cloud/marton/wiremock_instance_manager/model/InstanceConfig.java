package cloud.marton.wiremock_instance_manager.model;

import java.util.ArrayList;
import java.util.List;

public class InstanceConfig {

    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private final List<WireMockInstance> instances = new ArrayList<>();

    public InstanceConfig() {}

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<WireMockInstance> getInstances() { return instances; }
}
