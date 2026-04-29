const { MongoClient } = require('mongodb');

async function checkAllTestingEmailPublishers() {
  const client = new MongoClient('YOUR_MONGODB_URI_HERE');
  
  try {
    await client.connect();
    const db = client.db('mobilize');
    const companies = db.collection('companies');
    
    console.log('🔍 All Publishers with testing@gmail.com:');
    console.log('=======================================');
    
    // Find all companies with email = testing@gmail.com
    const publishers = await companies.find({ 
      $or: [
        { email: 'testing@gmail.com' },
        { emailId: 'testing@gmail.com' }
      ]
    }).toArray();
    
    console.log('Total testing@gmail.com publishers found:', publishers.length);
    console.log('');
    
    publishers.forEach((publisher, i) => {
      console.log((i+1) + '. ' + publisher.name);
      console.log('   ID:', publisher._id);
      console.log('   Email:', publisher.email || publisher.emailId);
      console.log('   Status:', publisher.status);
      console.log('   Admin ID:', publisher.adminId || 'NULL');
      console.log('   Created:', publisher.createdAt);
      console.log('');
    });
    
    await client.close();
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

checkAllTestingEmailPublishers();
