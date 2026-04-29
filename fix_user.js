const { MongoClient } = require('mongodb');
const bcrypt = require('bcrypt');

async function fixUser() {
    const uri = 'YOUR_MONGODB_URI_HERE';
    const client = new MongoClient(uri);
    const password = '12345678';
    const hashedPassword = await bcrypt.hash(password, 10);

    try {
        await client.connect();
        const db = client.db('mobilize');
        const users = db.collection('users');
        const superAdmins = db.collection('super_admins');

        console.log('--- Fixing sonuskyadav50@gmail.com ---');

        // 1. Update in Users collection (for Admin Portal)
        const userUpdate = await users.updateOne(
            { emailAddress: 'sonuskyadav50@gmail.com' },
            {
                $set: {
                    password: hashedPassword,
                    userType: 'ADMIN',
                    accountStatus: 'ACTIVE',
                    updatedAt: new Date()
                }
            },
            { upsert: true }
        );
        console.log('Users collection update:', userUpdate.matchedCount ? 'Updated' : 'Created');

        // 2. Add to SuperAdmins collection (for Super Admin Portal)
        const saUpdate = await superAdmins.updateOne(
            { email: 'sonuskyadav50@gmail.com' },
            {
                $set: {
                    name: 'Sonu Yadav',
                    email: 'sonuskyadav50@gmail.com',
                    password: hashedPassword, // The backend uses BCryptPasswordEncoder which is compatible
                    role: 'MASTER_SUPER_ADMIN',
                    isLocked: false,
                    failedAttempts: 0,
                    permissions: {
                        dashboard: true,
                        analytics: true,
                        admins: true,
                        publishers: true,
                        ads: true,
                        revenue: true,
                        transactions: true,
                        tickets: true,
                        auditLogs: true,
                        profile: true,
                        settings: true,
                        subAdmins: true
                    }
                }
            },
            { upsert: true }
        );
        console.log('SuperAdmins collection update:', saUpdate.matchedCount ? 'Updated' : 'Created');

        console.log('\n✅ User sonuskyadav50@gmail.com has been updated/created in both collections.');
        console.log('Password set to: 12345678');

        await client.close();
    } catch (error) {
        console.error('Error:', error.message);
    }
}

fixUser();
