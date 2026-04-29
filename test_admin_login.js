fetch('http://localhost:8081/api/admin/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    identifier: 'sonuskyadav50@gmail.com',
    password: '12345678'
  })
})
  .then(response => {
    console.log('🔐 Admin Login Test:');
    console.log('====================');
    console.log('Status:', response.status);
    return response.text();
  })
  .then(text => {
    console.log('Response:', text);
  })
  .catch(error => console.error('❌ Error:', error.message));
