const { MongoClient } = require('mongodb');

async function linkUnlinkedPublishers() {
  const client = new MongoClient('YOUR_MONGODB_URI_HERE');
  
  try {
    await client.connect();
    const db = client.db('mobilize');
    const companies = db.collection('companies');
    
    console.log('🔗 Linking Unlinked Publishers to sonuskyadav50:');
    console.log('==============================================');
    
    // Update publishers with adminId = NULL to link them to sonuskyadav50
    const result = await companies.updateMany(
      { 
        adminId: { $exists: false },
        $or: [
          { email: 'testing@gmail.com' },
          { emailId: 'testing@gmail.com' }
        ]
      },
      { 
        $set: { 
          adminId: '69f1f2958193c8ea10c8de4d',
          updatedAt: new Date()
        }
      }
    );
    
    console.log('✅ Publishers linked successfully!');
    console.log('   Matched:', result.matchedCount);
    console.log('   Modified:', result.modifiedCount);
    
    // Verify the update
    console.log('\n🔍 Verification:');
    const linkedPublishers = await companies.find({ 
      adminId: '69f1f2958193c8ea10c8de4d' 
    }).toArray();
    
    console.log('Total publishers linked to sonuskyadav50:', linkedPublishers.length);
    linkedPublishers.forEach((publisher, i) => {
      console.log('  ' + (i+1) + '. ' + publisher.name + ' - ' + (publisher.email || publisher.emailId));
    });
    
    await client.close();
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

linkUnlinkedPublishers();
