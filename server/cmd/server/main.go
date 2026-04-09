package main

import (
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/api"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
)

func main() {

	repository.InitDB()

	userRepo := repository.NewUserRepository()
	dropRepo := repository.NewDropRepository()
	collectionRepo := repository.NewCollectionRepository()

	authHandler := api.NewAuthHandler(userRepo)
	dropHandler := api.NewDropHandler(dropRepo, userRepo)
	collectionHandler := api.NewCollectionHandler(collectionRepo, dropRepo)

	r := api.SetupRouter(authHandler, dropHandler, collectionHandler) 
	r.Run()
}
