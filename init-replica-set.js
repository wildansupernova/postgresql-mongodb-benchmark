// Wait a bit for MongoDB to be fully started
sleep(2000);

// Initialize replica set with single member
rs.initiate({
  _id: "rs0",
  members: [
    {
      _id: 0,
      host: "localhost:27017"
    }
  ]
});

// Wait for replica set to be initialized
sleep(2000);

// Verify replica set status
print("Replica set status:");
print(JSON.stringify(rs.status(), null, 2));
