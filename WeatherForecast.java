import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.swing.Timer; // Added import for Timer

public class WeatherAPIData extends JFrame {
    private JTextField searchTextField;
    private JLabel temperatureText, weatherConditionDesc, humidityText, windspeedText;
    private Timer weatherUpdateTimer; // Declare timer variable

    public WeatherAPIData() {
        super("Weather Forecast");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(450, 650);
        setLocationRelativeTo(null);
        setLayout(null);
        setResizable(false);
        addGuiComponents();
    }

    private void addGuiComponents() {
        searchTextField = new JTextField();
        searchTextField.setBounds(15, 15, 351, 45);
        searchTextField.setFont(new Font("Dialog", Font.PLAIN, 24));
        add(searchTextField);

        JButton searchButton = new JButton(loadImage("src/search.png"));
        searchButton.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        searchButton.setBounds(375, 13, 47, 45);
        searchButton.addActionListener(e -> fetchWeatherData(searchTextField.getText()));
        add(searchButton);

        temperatureText = new JLabel("-- °C");
        temperatureText.setBounds(0, 350, 450, 54);
        temperatureText.setFont(new Font("Dialog", Font.BOLD, 48));
        temperatureText.setHorizontalAlignment(SwingConstants.CENTER);
        add(temperatureText);

        weatherConditionDesc = new JLabel("Condition: --");
        weatherConditionDesc.setBounds(0, 405, 450, 36);
        weatherConditionDesc.setFont(new Font("Dialog", Font.PLAIN, 32));
        weatherConditionDesc.setHorizontalAlignment(SwingConstants.CENTER);
        add(weatherConditionDesc);

        humidityText = new JLabel("Humidity: --%");
        humidityText.setBounds(90, 500, 150, 55);
        humidityText.setFont(new Font("Dialog", Font.PLAIN, 16));
        add(humidityText);

        windspeedText = new JLabel("Wind Speed: -- kph");
        windspeedText.setBounds(250, 500, 200, 55);
        windspeedText.setFont(new Font("Dialog", Font.PLAIN, 16));
        add(windspeedText);
    }

    private void fetchWeatherData(String city) {
        try {
            JSONObject cityData = getLocationData(city);
            if (cityData == null) {
                JOptionPane.showMessageDialog(this, "Location not found!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double latitude = ((Number) cityData.get("latitude")).doubleValue();
            double longitude = ((Number) cityData.get("longitude")).doubleValue();
            updateWeatherUI(latitude, longitude);

            // Start real-time updates
            if (weatherUpdateTimer != null) {
                weatherUpdateTimer.stop();
            }

            weatherUpdateTimer = new Timer(30000, e -> updateWeatherUI(latitude, longitude)); // 30-second interval
            weatherUpdateTimer.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateWeatherUI(double latitude, double longitude) {
        JSONObject weatherData = getWeatherData(latitude, longitude);
        if (weatherData != null) {
            temperatureText.setText(weatherData.get("temperature") + " °C");
            weatherConditionDesc.setText("Condition: " + weatherData.get("condition"));
            humidityText.setText("Humidity: " + weatherData.getOrDefault("humidity", "--") + "%");
            windspeedText.setText("Wind Speed: " + weatherData.getOrDefault("windspeed", "--") + " kph");
            System.out.println("Weather data updated!");
        }
    }

    private JSONObject getLocationData(String city) {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String urlString = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + encodedCity + "&count=1&language=en&format=json";

            HttpURLConnection connection = fetchApiResponse(urlString);
            if (connection == null || connection.getResponseCode() != 200) {
                System.out.println("Error: API request failed.");
                return null;
            }

            String jsonResponse = readApiResponse(connection);
            System.out.println("Geolocation API Response: " + jsonResponse); // Debugging output

            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonResponse);
            JSONArray results = (JSONArray) jsonObject.get("results");

            return (results != null && !results.isEmpty()) ? (JSONObject) results.get(0) : null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject getWeatherData(double latitude, double longitude) {
        try {
            String urlString = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weathercode&timezone=auto";

            HttpURLConnection connection = fetchApiResponse(urlString);
            if (connection == null || connection.getResponseCode() != 200) return null;

            String jsonResponse = readApiResponse(connection);
            System.out.println("Weather API Response: " + jsonResponse); // Debugging output

            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonResponse);
            JSONObject current = (JSONObject) jsonObject.get("current");

            if (current == null) return null;

            JSONObject weatherData = new JSONObject();
            weatherData.put("temperature", current.get("temperature_2m"));
            weatherData.put("humidity", current.getOrDefault("relative_humidity_2m", "--"));
            weatherData.put("windspeed", current.getOrDefault("wind_speed_10m", "--"));
            weatherData.put("condition", getWeatherCondition(((Number) current.get("weathercode")).intValue()));

            return weatherData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getWeatherCondition(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "Clear Sky";
            case 1, 2, 3 -> "Partly Cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67 -> "Rainy";
            case 80, 81, 82 -> "Showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    private HttpURLConnection fetchApiResponse(String urlString) {
        try {
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            return connection;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String readApiResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ImageIcon loadImage(String resourcePath) {
        try {
            BufferedImage image = ImageIO.read(new File(resourcePath));
            return new ImageIcon(image);
        } catch (IOException e) {
            System.out.println("Could not find resource: " + resourcePath);
        }
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WeatherAPIData app = new WeatherAPIData();
            app.setVisible(true);
        });
    }
}
