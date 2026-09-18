-- A flow created before the owner column was added: it has no owner, but must stay readable.
INSERT INTO flow(flow_id, tenant_id, type, status, started_at)
VALUES ('aa000000-0000-0000-0000-0000000000aa', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE', 'IN_PROGRESS',
        '2020-01-01 00:00:00+00');

-- A flow owned by an MTE instance with an application flow resolving ownership through the parent flow.
INSERT INTO flow(flow_id, tenant_id, type, status, started_at, owner_instance_id)
VALUES ('aa000000-0000-0000-0000-0000000000bb', '6ad28dae-7c02-4f89-9320-153c55bf1914', 'ENTITLE', 'IN_PROGRESS',
        '2020-01-01 00:00:00+00', 'aa000000-0000-0000-0000-0000000000ff');

INSERT INTO application_flow(application_flow_id, application_id, application_name, application_version,
                             tenant_id, flow_id, type, status, started_at)
VALUES ('aa000000-0000-0000-0000-0000000000cc', 'test-app-1.0.0', 'test-app', '1.0.0',
        '6ad28dae-7c02-4f89-9320-153c55bf1914', 'aa000000-0000-0000-0000-0000000000bb', 'ENTITLE', 'IN_PROGRESS',
        '2020-01-01 00:00:00+00');
