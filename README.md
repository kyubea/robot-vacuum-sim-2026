# Robot Vacuum Simulator

CS 499 Senior Design Project - Spring 2026

## Quick Start

### Prerequisites
- Java 17 or higher
- Git
- VS Code

### Setup
```bash
# Clone the repository
git clone https://github.com/kyubea/robot-vacuum-sim-2026.git
cd robot-vacuum-sim-2026

# Build the project (downloads Gradle automatically)
./gradlew build

# Run the application
./gradlew run
```

You should see: `Robot Vacuum Simulator`

### Project Structure
```
src/
├── main/
│   ├── java/com/vacuum/
│   │   ├── model/          # Data structures 
│   │   ├── ui/             # User interface
│   │   ├── simulation/     # Algorithms and engine
│   │   ├── rendering/      # Visualization 
│   │   └── util/           # Shared utilities
│   └── resources/          # FXML, CSS, images
└── test/                   # Unit tests
```

### Development Workflow

1. Pull latest: `git pull origin main`
2. Create feature branch: `git checkout -b feature/your-name`
3. Make changes in your assigned directory
4. Test: `./gradlew build`
5. Commit: `git commit -m "Description"`
6. Push: `git push origin feature/your-name`
7. Create Pull Request on GitHub
