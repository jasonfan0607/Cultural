import http from 'http';
import url from 'url';

const server = http.createServer((req, res) => {
  // 设置 CORS 头
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  
  // 处理 OPTIONS 请求
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  
  // 模拟 API 响应
  if (pathname.startsWith('/api/')) {
    const apiPath = pathname.replace('/api/', '');
    
    // 设置默认响应
    let response = {
      code: 200,
      message: 'success',
      data: null
    };
    
    // 根据不同的 API 路径返回不同的模拟数据
    if (apiPath === 'heritage/list') {
      response.data = {
        records: [
          {
            id: 1,
            name: '南京云锦',
            description: '南京云锦是中国传统的丝织工艺品，有着1600多年的历史。',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Nanjing%20Yunjin%20silk%20fabric%20traditional%20Chinese%20craft&image_size=landscape_16_9',
            region: '南京'
          },
          {
            id: 2,
            name: '苏州园林',
            description: '苏州园林是中国古典园林的杰出代表，以精巧的设计和优美的景观著称。',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Suzhou%20classical%20garden%20Chinese%20traditional%20architecture&image_size=landscape_16_9',
            region: '苏州'
          }
        ],
        total: 2
      };
    } else if (apiPath === 'celebrity/list') {
      response.data = {
        records: [
          {
            id: 1,
            name: '曹雪芹',
            description: '清代小说家，《红楼梦》的作者。',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Chinese%20historical%20figure%20Cao%20Xueqin%20writer&image_size=portrait_4_3',
            birthYear: '1715',
            deathYear: '1763'
          },
          {
            id: 2,
            name: '徐悲鸿',
            description: '现代画家，擅长画马。',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Chinese%20modern%20painter%20Xu%20Beihong%20portrait&image_size=portrait_4_3',
            birthYear: '1895',
            deathYear: '1953'
          }
        ],
        total: 2
      };
    } else if (apiPath === 'art/list') {
      response.data = {
        records: [
          {
            id: 1,
            name: '昆曲',
            description: '昆曲是中国最古老的戏曲剧种之一，被称为“百戏之祖”。',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Kunqu%20opera%20traditional%20Chinese%20performance&image_size=landscape_16_9',
            region: '苏州'
          },
          {
            id: 2,
            name: '苏绣',
            description: '苏绣是苏州地区的传统刺绣工艺，以精细著称。',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Suzhou%20embroidery%20traditional%20Chinese%20craft&image_size=landscape_16_9',
            region: '苏州'
          }
        ],
        total: 2
      };
    } else if (apiPath === 'news/list') {
      response.data = {
        records: [
          {
            id: 1,
            title: '江苏文化遗产保护工作取得新进展',
            content: '近日，江苏省文化和旅游厅发布消息称，全省文化遗产保护工作取得显著成效...',
            publishTime: '2026-04-15',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Chinese%20cultural%20heritage%20protection%20work&image_size=landscape_16_9'
          },
          {
            id: 2,
            title: '苏州园林被列入世界文化遗产名录20周年',
            content: '今年是苏州园林被列入世界文化遗产名录20周年，苏州市举办了系列庆祝活动...',
            publishTime: '2026-04-10',
            imageUrl: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Suzhou%20garden%20world%20cultural%20heritage%20celebration&image_size=landscape_16_9'
          }
        ],
        total: 2
      };
    } else if (apiPath === 'auth/login') {
      // 模拟登录成功
      response.data = {
        token: 'mock-token-123456',
        userInfo: {
          id: 1,
          username: 'test',
          nickname: '测试用户',
          avatar: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20profile%20picture&image_size=square'
        }
      };
    }
    
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(response));
  } else {
    // 处理其他请求
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      code: 404,
      message: 'Not Found',
      data: null
    }));
  }
});

const PORT = 8080;
server.listen(PORT, '127.0.0.1', () => {
  console.log(`Mock API server running at http://127.0.0.1:${PORT}/`);
});
