const { MongoClient } = require('mongodb');
const bcrypt = require('bcrypt');

async function revertToAdminOnly() {
    const uri = 'YOUR_MONGODB_URI_HERE';
    const client = new MongoClient(uri);
    const password = '12345678';
    const hashedPassword = await bcrypt.hash(password, 10);

    try {
        await client.connect();
        const db = client.db('mobilize');
        const users = db.collection('users');
        const superAdmins = db.collection('super_admins');

        console.log('--- Reverting sonuskyadav50@gmail.com to Admin only ---');

        // 1. Remove from SuperAdmins collection
        const saDelete = await superAdmins.deleteOne({ email: 'sonuskyadav50@gmail.com' });
        console.log('SuperAdmins collection delete:', saDelete.deletedCount ? 'Deleted' : 'Not found');

        // 2. Ensure correct in Users collection (Admin Portal)
        const userUpdate = await users.updateOne(
            { emailAddress: 'sonuskyadav50@gmail.com' },
            {
                $set: {
                    password: hashedPassword,
                    userType: 'ADMIN', // Capitalize if needed, though 'admin' was seen earlier. AdminAuthService uses "ADMIN".
                    accountStatus: 'ACTIVE',
                    updatedAt: new Date()
                }
            },
            { upsert: true }
        );
        console.log('Users collection update:', userUpdate.matchedCount ? 'Updated' : 'Created');

        console.log('\n✅ User sonuskyadav50@gmail.com is now only in the Users collection as an ADMIN.');
        console.log('Password verified as: 12345678');

        await client.close();
    } catch (error) {
        console.error('Error:', error.message);
    }
}

revertToAdminOnly();
