package repository

import (
	"fmt"
	"log"
	"os"

	"github.com/joho/godotenv"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/model"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

var DB *gorm.DB

func InitDB() {

	err := godotenv.Load()

	if err != nil {
		log.Fatalln("Couldn't load ENV variables.")
	}

	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=disable",
        os.Getenv("DB_HOST"),
        os.Getenv("DB_USER"),
        os.Getenv("DB_PASSWORD"),
        os.Getenv("DB_NAME"),
        os.Getenv("DB_PORT"),
    )

	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})

	if err != nil {
		log.Fatalln("Couldn't connect to the database.")
	}

	err = db.AutoMigrate(
		&model.User{},
		&model.Drop{},
		&model.Collection{},
	)

	if err != nil {
		log.Fatalln("Database migration failed.")
	}

	DB = db
	log.Println("Database initialized successfully.")
}
