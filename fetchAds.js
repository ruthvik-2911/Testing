import axios from 'axios';

async function fetchAds() {
  try {
    const res = await axios.get('http://ec2-15-206-186-192.ap-south-1.compute.amazonaws.com:3000/v1/ad-campaigns?page=1&limit=50', {
      headers: {
        Authorization: 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiI2NGZiMTE3YS1mNTMwLTRmOTgtYTVkMy0yMWY3ZmVlYzkwNDciLCJfaWQiOiI2NGVkODZkMjMyMDBlMWQ2YzUyMDYwYmMiLCJpYXQiOjE3NzY5MTQ0MzksImV4cCI6MTgwODQ1MDQzOX0.jYRhILMRhluttAgns-OGDgGdji0DKhok6QBcUB7qdPg'
      }
    });
    const campaigns = res.data.data;
    campaigns.forEach(c => {
      const adUid = c.advertisementId ? c.advertisementId.uid : 'NO_AD_UID';
      const title = c.advertisementId ? c.advertisementId.title : 'NO_TITLE';
      console.log(`Camp UID: ${c.uid} | Ad UID: ${adUid} | Title: ${title} | Status: ${c.compaignsStatus} | Created: ${c.createdAt}`);
    });
  } catch (err) {
    console.error(err.message);
  }
}
fetchAds();
