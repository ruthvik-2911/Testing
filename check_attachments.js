const { MongoClient } = require('mongodb');

async function checkAttachments() {
    const uri = 'YOUR_MONGODB_URI_HERE';
    const client = new MongoClient(uri);

    try {
        await client.connect();
        const db = client.db('mobilize');
        const messages = db.collection('ticket_messages');

        console.log('--- Checking Ticket Messages and Attachments ---');
        const recentMessages = await messages.find({ attachmentUrl: { $exists: true, $ne: null } })
            .sort({ createdAt: -1 })
            .limit(5)
            .toArray();

        if (recentMessages.length === 0) {
            console.log('No messages with attachments found.');
        } else {
            recentMessages.forEach(msg => {
                console.log(`Ticket: ${msg.ticketId}`);
                console.log(`Sender: ${msg.senderType}`);
                console.log(`Message: ${msg.message}`);
                console.log(`URL: ${msg.attachmentUrl}`);
                console.log(`URLs: ${JSON.stringify(msg.attachmentUrls)}`);
                console.log('---');
            });
        }

        await client.close();
    } catch (error) {
        console.error('Error:', error.message);
    }
}

checkAttachments();
