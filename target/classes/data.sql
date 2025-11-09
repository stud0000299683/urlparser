-- Очистка таблиц
DELETE FROM url_results;
DELETE FROM urls;

-- Вставка начальных URL по которым производим парсинг
INSERT INTO urls (url, name, description, created_at, active) VALUES
('https://jsonplaceholder.typicode.com/users', 'JSONPlaceholder Users', 'Test API for users data', CURRENT_TIMESTAMP, true),
('https://api.github.com/users', 'GitHub Users API', 'GitHub public users API', CURRENT_TIMESTAMP, true),
('https://reqres.in/api/users', 'ReqRes API', 'Test API for user operations', CURRENT_TIMESTAMP, true),
('https://httpbin.org/json', 'HTTPBin JSON', 'HTTP testing service', CURRENT_TIMESTAMP, true),
('https://catfact.ninja/fact', 'Cat Facts API', 'Random cat facts', CURRENT_TIMESTAMP, true),
('https://dog.ceo/api/breeds/image/random', 'Dog CEO API', 'Random dog images', CURRENT_TIMESTAMP, true),
('https://api.agify.io/?name=alex', 'Agify API', 'Age prediction by name', CURRENT_TIMESTAMP, true),
('https://api.genderize.io/?name=alex', 'Genderize API', 'Gender prediction by name', CURRENT_TIMESTAMP, true),
('https://api.nationalize.io/?name=alex', 'Nationalize API', 'Nationality prediction by name', CURRENT_TIMESTAMP, true),
('https://www.boredapi.com/api/activity', 'Bored API', 'Random activities', CURRENT_TIMESTAMP, true),
('https://api.publicapis.org/entries', 'Public APIs', 'List of public APIs', CURRENT_TIMESTAMP, true),
('https://api.zippopotam.us/us/90210', 'Zippopotam API', 'Zip code information', CURRENT_TIMESTAMP, true),
('https://datausa.io/api/data?drilldowns=Nation&measures=Population', 'Data USA API', 'US population data', CURRENT_TIMESTAMP, true),
('https://api.kanye.rest/', 'Kanye Rest API', 'Random Kanye West quotes', CURRENT_TIMESTAMP, true),
('https://official-joke-api.appspot.com/random_joke', 'Joke API', 'Random jokes', CURRENT_TIMESTAMP, true),
('https://randomuser.me/api/', 'Random User API', 'Random user data', CURRENT_TIMESTAMP, true),
('https://api.adviceslip.com/advice', 'Advice Slip API', 'Random advice', CURRENT_TIMESTAMP, true),
('https://www.thecocktaildb.com/api/json/v1/1/random.php', 'Cocktail DB API', 'Random cocktail recipes', CURRENT_TIMESTAMP, true),
('https://api.coindesk.com/v1/bpi/currentprice.json', 'CoinDesk API', 'Bitcoin price index', CURRENT_TIMESTAMP, true),
('https://openlibrary.org/books/OL7353617M.json', 'Open Library API', 'Book information', CURRENT_TIMESTAMP, true);