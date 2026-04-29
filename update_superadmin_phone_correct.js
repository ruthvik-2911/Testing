const { MongoClient } = require('mongodb');

async function updateSuperAdminPhone() {
  const client = new MongoClient('YOUR_MONGODB_URI_HERE');
  
  try {
    await client.connect();
    const db = client.db('mobilize');
    const users = db.collection('users');
    
    console.log('📱 Updating SuperAdmin Phone Number (Correct Format):');
    console.log('=======================================================');
    
    // Update sonuskyadav50 admin user with correct phoneNumber object structure
    const result = await users.updateOne(
      { emailAddress: 'sonuskyadav50@gmail.com' },
      { 
        $set: { 
          phoneNumber: {
            countryCode: '+91',
            dialNumber: '6363742403'
          },
          updatedAt: new Date()
        }
      }
    );
    
    if (result.matchedCount > 0) {
      console.log('✅ SuperAdmin phone number updated successfully!');
      console.log('   Email: sonuskyadav50@gmail.com');
      console.log('   Phone: +91 6363742403');
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
      if (updatedUser.phoneNumber) {
        console.log('   Country Code:', updatedUser.phoneNumber.countryCode);
        console.log('   Dial Number:', updatedUser.phoneNumber.dialNumber);
      }
    }
    
    await client.close();
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

updateSuperAdminPhone();
