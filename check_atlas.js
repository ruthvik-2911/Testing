const { MongoClient } = require('mongodb');

const uri = "mongodb+srv://ruthvikg00_db_user:c4k51DIu0Rolx6ne@cluster0.ynrgzwo.mongodb.net/";
const dbName = "keliri";

async function main() {
    const client = new MongoClient(uri);
    try {
        await client.connect();
        console.log("Connected to Atlas");
        const db = client.db(dbName);

        const admins = await db.collection('admins').find({}).toArray();
        console.log("Admins Count:", admins.length);
        console.log("Admins Sample:", JSON.stringify(admins, null, 2));

        const regs = await db.collection('admin_registrations').find({}).toArray();
        console.log("Registrations Count:", regs.length);
        console.log("Registrations Sample:", JSON.stringify(regs, null, 2));

    } catch (e) {
        console.error(e);
    } finally {
        await client.close();
    }
}

main();
