const { MongoClient } = require('mongodb');

async function updateSuperAdminPhone() {
  const client = new MongoClient('YOUR_MONGODB_URI_HERE');
  
  try {
    await client.connect();
    const db = client.db('mobilize');
    const users = db.collection('users');
    
    console.log('📱 Updating SuperAdmin Phone Number:');
    console.log('====================================');
    
    // Update sonuskyadav50 admin user
    const result = await users.updateOne(
      { emailAddress: 'sonuskyadav50@gmail.com' },
      { 
        $set: { 
          phoneNumber: '+916363742403',
          phone: '+916363742403',
          updatedAt: new Date()
        }
      }
    );
    
    if (result.matchedCount > 0) {
      console.log('✅ SuperAdmin phone number updated successfully!');
      console.log('   Email: sonuskyadav50@gmail.com');
      console.log('   Phone: +916363742403');
      console.log('   Matched:', result.matchedCount);
      console.log('   Modified:', result.modifiedCount);
    } else {
      console.log('❌ SuperAdmin user not found');
    }
    
    // Verify the update
    const updatedUser = await users.findOne({ emailAddress: 'sonuskyadav50@gmail.com' });
    if (updatedUser) {
      console.log('\n🔍 Verification:');
      console.log('   Phone Number:', updatedUser.phoneNumber);
      console.log('   Phone:', updatedUser.phone);
    }
    
    await client.close();
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

updateSuperAdminPhone();
