import { MongoClient } from 'mongodb';

function redactMongoUri(uri) {
  return String(uri || '<empty>').replace(/\/\/([^/@]+)@/, '//<credentials>@');
}

function atlasNetworkHint(error) {
  const message = `${error?.name || ''}\n${error?.message || ''}\n${error?.cause?.message || ''}`;
  if (/tlsv1 alert internal error|ERR_SSL_TLSV1_ALERT_INTERNAL_ERROR|SSL routines/i.test(message)) {
    return [
      'Atlas rejected the TLS handshake. In this workflow, that usually means the target Atlas cluster network access list does not allow the GitHub Actions runner egress IP, or the MongoDB URI points at the wrong Atlas cluster/account.',
      'Fix the Atlas Network Access settings for the template cluster, or run the refresh from a runner/VPC whose egress IP is allow-listed.'
    ];
  }

  if (/Authentication failed|bad auth/i.test(message)) {
    return ['Check that the MongoDB username/password in the selected GitHub secret are valid for this cluster.'];
  }

  return [];
}

export async function connectMongo(uri, { appName, label }) {
  const client = new MongoClient(uri, {
    appName,
    connectTimeoutMS: 10000,
    serverSelectionTimeoutMS: 20000
  });

  try {
    await client.connect();
    console.log(`Connected to ${label} MongoDB: ${redactMongoUri(uri)}`);
    return client;
  } catch (error) {
    await client.close().catch(() => {});

    const lines = [
      `Could not connect to ${label} MongoDB: ${redactMongoUri(uri)}`,
      ...atlasNetworkHint(error),
      `Original error: ${error?.name || 'Error'}: ${error?.message || String(error)}`
    ];

    throw new Error(lines.join('\n'));
  }
}
