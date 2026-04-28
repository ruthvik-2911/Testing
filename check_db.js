const mongoose = require('mongoose');

const mongoUri = 'mongodb://13.200.246.214:27017/keliri_production';

async function checkCompanies() {
    try {
        await mongoose.connect(mongoUri, { useNewUrlParser: true, useUnifiedTopology: true });
        console.log('Connected to Production DB');

        const db = mongoose.connection.db;
        const collections = await db.listCollections().toArray();
        console.log('Collections:', collections.map(c => c.name));

        const companies = db.collection('companies');
        const count = await companies.countDocuments();
        console.log('Total Companies:', count);

        const pending = await companies.countDocuments({ status: false });
        console.log('Companies with status:false:', pending);

        const firstFew = await companies.find({}).limit(5).toArray();
        console.log('Sample Companies:', JSON.stringify(firstFew, null, 2));

        process.exit(0);
    } catch (error) {
        console.error('Error:', error);
        process.exit(1);
    }
}

checkCompanies();
