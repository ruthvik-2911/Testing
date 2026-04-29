const { MongoClient } = require('mongodb');

async function checkUser() {
    const uri = 'YOUR_MONGODB_URI_HERE';
    const client = new MongoClient(uri);

    try {
        await client.connect();
        const db = client.db('mobilize');
        const users = db.collection('users');
        const superAdmins = db.collection('super_admins');

        console.log('Checking Users collection for sonuskyadav50@gmail.com:');
        const user = await users.findOne({ emailAddress: 'sonuskyadav50@gmail.com' });
        if (user) {
            console.log('Found in Users:', JSON.stringify({
                id: user._id,
                email: user.emailAddress,
                userType: user.userType,
                accountStatus: user.accountStatus,
                hasPassword: !!user.password,
                passwordStart: user.password ? user.password.substring(0, 10) : 'none'
            }, null, 2));
        } else {
            console.log('Not found in Users collection.');
        }

        console.log('\nChecking SuperAdmins collection for sonuskyadav50@gmail.com:');
        const sa = await superAdmins.findOne({ email: 'sonuskyadav50@gmail.com' });
        if (sa) {
            console.log('Found in SuperAdmins:', JSON.stringify({
                id: sa._id,
                email: sa.email,
                role: sa.role,
                isLocked: sa.isLocked,
                hasPassword: !!sa.password,
                passwordStart: sa.password ? sa.password.substring(0, 10) : 'none'
            }, null, 2));
        } else {
            console.log('Not found in SuperAdmins collection.');
        }

        await client.close();
    } catch (error) {
        console.error('Error:', error.message);
    }
}

checkUser();
