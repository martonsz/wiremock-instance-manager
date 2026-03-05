package cloud.marton.wiremock_instance_manager.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WireMockInstance {

    private String id;
    private String name;
    private int port;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private InstanceStatus status = InstanceStatus.STOPPED;

    private List<WireMockOption> options = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public WireMockInstance() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public InstanceStatus getStatus() { return status; }
    public void setStatus(InstanceStatus status) { this.status = status; }

    public List<WireMockOption> getOptions() { return options; }
    public void setOptions(List<WireMockOption> options) { this.options = options; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
