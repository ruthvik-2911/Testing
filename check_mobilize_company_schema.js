const mongoose = require('mongoose');

async function checkSchema() {
    try {
        await mongoose.connect('mongodb+srv://ruthvikg00_db_user:c4k51DIu0Rolx6ne@cluster0.ynrgzwo.mongodb.net/keliri');
        console.log('Connected to MongoDB');

        const Company = mongoose.connection.collection('companies');
        const company = await Company.findOne({});

        if (company) {
            console.log('Found a company record:');
            console.log(JSON.stringify(company, null, 2));
        } else {
            console.log('No company records found in "companies" collection');
        }

        await mongoose.disconnect();
    } catch (err) {
        console.error('Error:', err);
    }
}

checkSchema();
