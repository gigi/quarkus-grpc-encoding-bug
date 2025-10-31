import grpc from 'k6/net/grpc';
import {check} from 'k6';

const client = grpc.Client();
client.load(['src/main/proto'], 'hello.proto');

export const options = {
    vus: 1,
    iterations: 1,
};

export default function () {
    // Connect to the gRPC server
    client.connect('localhost:8080', {
        plaintext: true,
    });

    console.log('\n=== Test 1: gRPC call WITH gzip compression ===');

    // Call 1: WITH gzip compression
    // k6 uses the 'grpc-encoding' metadata to enable compression
    const responseWithGzip = client.invoke('hello.HelloGrpc/SayHello', {
        name: 'k6-with-gzip',
    }, {
        metadata: {
            'grpc-encoding': 'gzip',
            'grpc-accept-encoding': 'gzip',
        },
    });

    console.log('Request with gzip:');
    console.log('  - Status:', responseWithGzip.status);
    console.log('  - Message:', responseWithGzip.message.message);
    console.log('  - Metadata:', JSON.stringify(responseWithGzip));

    check(responseWithGzip, {
        'with gzip: status is OK': (r) => r && r.status === grpc.StatusOK,
        'with gzip: response message is correct': (r) => r && r.message && r.message.message === 'Hello k6-with-gzip!',
        'with gzip: response has grpc-encoding header': (r) => r && r.headers && 'grpc-encoding' in r.headers,
    });

    // Log compression info
    if (responseWithGzip.headers && responseWithGzip.headers['grpc-encoding']) {
        console.log('  - Response compressed with:', responseWithGzip.headers['grpc-encoding']);
    } else {
        console.log('  - Response NOT compressed (no grpc-encoding header)');
    }

    console.log('\n=== Test 2: gRPC call WITHOUT gzip compression ===');

    // Call 2: WITHOUT gzip compression (only identity)
    const responseWithoutGzip = client.invoke('hello.HelloGrpc/SayHello', {
        name: 'k6-without-gzip',
    }, {
        metadata: {
            'grpc-accept-encoding': 'identity',
        },
    });

    console.log('Request without gzip (identity only):');
    console.log('  - Status:', responseWithoutGzip.status);
    console.log('  - Message:', responseWithoutGzip.message.message);
    console.log('  - Metadata:', JSON.stringify(responseWithoutGzip.headers));

    check(responseWithoutGzip, {
        'without gzip: status is OK': (r) => r && r.status === grpc.StatusOK,
        'without gzip: response message is correct': (r) => r && r.message && r.message.message === 'Hello k6-without-gzip!',
        'without gzip: response is NOT compressed': (r) => {
            if (!r || !r.headers) return true; // no metadata means no compression
            const encoding = r.headers['grpc-encoding'];
            return !encoding || encoding === 'identity';
        },
    });

    // Log compression info
    if (responseWithoutGzip.headers && responseWithoutGzip.headers['grpc-encoding']) {
        console.log('  - Response encoding:', responseWithoutGzip.headers['grpc-encoding']);
    } else {
        console.log('  - Response NOT compressed (no grpc-encoding header)');
    }

    console.log('\n=== Test 3: gRPC call with NO accept-encoding header ===');

    // Call 3: Without any compression headers (client doesn't advertise support)
    const responseNoHeader = client.invoke('hello.HelloGrpc/SayHello', {
        name: 'k6-no-header',
    });

    console.log('Request with no accept-encoding header:');
    console.log('  - Status:', responseNoHeader.status);
    console.log('  - Message:', responseNoHeader.message.message);
    console.log('  - Metadata:', JSON.stringify(responseNoHeader.headers));

    check(responseNoHeader, {
        'no header: status is OK': (r) => r && r.status === grpc.StatusOK,
        'no header: response message is correct': (r) => r && r.message && r.message.message === 'Hello k6-no-header!',
        'no header: response is NOT compressed': (r) => {
            if (!r || !r.headers) return true;
            const encoding = r.headers['grpc-encoding'];
            return !encoding || encoding === 'identity';
        },
    });

    // Log compression info
    if (responseNoHeader.headers && responseNoHeader.headers['grpc-encoding']) {
        console.log('  - Response encoding:', responseNoHeader.headers['grpc-encoding']);
    } else {
        console.log('  - Response NOT compressed (no grpc-encoding header)');
    }

    console.log('\n=== Summary ===');
    console.log('Expected behavior:');
    console.log('  - Test 1: Server SHOULD compress with gzip (client accepts gzip)');
    console.log('  - Test 2: Server SHOULD NOT compress (client only accepts identity)');
    console.log('  - Test 3: Server SHOULD NOT compress (client does not advertise support)');
    console.log('');

    client.close();
}
